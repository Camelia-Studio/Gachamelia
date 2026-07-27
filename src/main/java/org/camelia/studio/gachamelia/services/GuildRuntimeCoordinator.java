package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class GuildRuntimeCoordinator implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GuildRuntimeCoordinator.class);
    private static final long[] DEACTIVATION_ATTEMPT_SECONDS = {0L, 10L, 60L};

    private final BotApiService botApiService;
    private final GuildCatalogueCache catalogueCache;
    private final Consumer<String> reconciliationCanceller;
    private final ExecutorService executor;
    private final ScheduledExecutorService retryScheduler;
    private final LongSupplier nanoTime;
    // Never wait for an admission, acquire a guild lock or invoke external code while holding this lock.
    private final Object lifecycleLock = new Object();
    private final Map<String, GuildSession> presentGuilds = new HashMap<>();
    private final Set<GuildSession> liveSessions = new HashSet<>();
    private final Set<String> expectedUnavailableGuildIds = new HashSet<>();
    private final Map<String, Object> guildLocks = new HashMap<>();
    private final Map<String, Deactivation> deactivations = new HashMap<>();
    private final Set<Transition> liveTransitions = new HashSet<>();
    private final CountDownLatch closeCompleted = new CountDownLatch(1);
    private boolean closed;
    private long nextEpoch;
    private volatile Consumer<Guild> readyHandler = ignored -> { };

    public GuildRuntimeCoordinator(
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            RuntimeConfiguration configuration,
            Consumer<String> reconciliationCanceller
    ) {
        this(
                botApiService,
                catalogueCache,
                reconciliationCanceller,
                Executors.newFixedThreadPool(Math.max(1, Math.min(4, configuration.memberSyncConcurrency()))),
                Executors.newSingleThreadScheduledExecutor(),
                System::nanoTime
        );
    }

    GuildRuntimeCoordinator(
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            Consumer<String> reconciliationCanceller,
            ExecutorService executor,
            ScheduledExecutorService retryScheduler
    ) {
        this(botApiService, catalogueCache, reconciliationCanceller, executor, retryScheduler, System::nanoTime);
    }

    GuildRuntimeCoordinator(
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            Consumer<String> reconciliationCanceller,
            ExecutorService executor,
            ScheduledExecutorService retryScheduler,
            LongSupplier nanoTime
    ) {
        this.botApiService = Objects.requireNonNull(botApiService);
        this.catalogueCache = Objects.requireNonNull(catalogueCache);
        this.reconciliationCanceller = Objects.requireNonNull(reconciliationCanceller);
        this.executor = Objects.requireNonNull(executor);
        this.retryScheduler = Objects.requireNonNull(retryScheduler);
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }

    public void setReadyHandler(Consumer<Guild> readyHandler) {
        this.readyHandler = Objects.requireNonNull(readyHandler);
    }

    public void startGuild(Guild guild) {
        GuildSession session = admitGuild(guild);
        if (session != null) {
            submit("synchronisation initiale", session.guildId(),
                    () -> synchronizeGuild(guild, session, true, false));
        }
    }

    public void refreshGuild(Guild guild) {
        GuildSession session = currentSession(guild.getId());
        if (session != null) {
            long refreshOwner = session.requestRefresh();
            if (refreshOwner != 0L) {
                submitRefresh(guild, session, refreshOwner);
            }
        }
    }

    public void recoverGuild(Guild guild) {
        GuildSession session = currentSession(guild.getId());
        if (session != null) {
            submit("récupération de la guilde", session.guildId(),
                    () -> synchronizeGuild(guild, session, false, true));
        }
    }

    public void expectUnavailableGuild(String guildId) {
        synchronized (lifecycleLock) {
            if (!closed) {
                expectedUnavailableGuildIds.add(guildId);
            }
        }
    }

    public void guildAvailable(Guild guild) {
        GuildSession session = admitGuild(guild);
        if (session != null) {
            submit("synchronisation d'une guilde redevenue disponible", session.guildId(),
                    () -> synchronizeGuild(guild, session, true, false));
        }
    }

    public void leaveGuild(String guildId) {
        GuildSession session;
        Transition transition = null;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            if (!presentGuilds.containsKey(guildId) && deactivations.containsKey(guildId)) {
                expectedUnavailableGuildIds.remove(guildId);
            } else {
                session = presentGuilds.get(guildId);
                if (session != null) {
                    session.assertCanStop("leaveGuild");
                    presentGuilds.remove(guildId);
                    session.stopAccepting();
                }
                expectedUnavailableGuildIds.remove(guildId);
                transition = new Transition(guildId, session);
                Deactivation deactivation = new Deactivation(
                        guildId,
                        nanoTime.getAsLong(),
                        guildLockLocked(guildId),
                        transition
                );
                transition.setDeactivation(deactivation);
                liveTransitions.add(transition);
                deactivations.put(guildId, deactivation);
            }
        }
        catalogueCache.remove(guildId);
        if (transition != null) {
            scheduleTransition(transition);
        }
    }

    public Optional<CatalogueEnvelope> findReadyCatalogue(String guildId) {
        GuildSession session = currentSession(guildId);
        if (session == null || !session.canObserveCatalogue()) {
            return Optional.empty();
        }
        return catalogueCache.findReady(guildId);
    }

    public <T> T executeRuntime(Guild guild, Supplier<T> operation) {
        String guildId = guild.getId();
        GuildSession session = requireCurrentSession(guildId);
        try {
            return executeAdmitted(session, operation);
        } catch (ApiException exception) {
            if (!isRecoverable(exception)) {
                throw exception;
            }
        }

        recoverSynchronously(guild, session);
        return executeAdmitted(session, operation);
    }

    public void refreshGuildEmojis(Guild guild) {
        GuildSession session = currentSession(guild.getId());
        if (session != null) {
            submit("rafraîchissement des emojis", session.guildId(),
                    () -> refreshGuildEmojisLocked(guild, session));
        }
    }

    @Override
    public void close() {
        List<GuildSession> sessions;
        List<Transition> transitions;
        Set<String> guildIds;
        boolean closeOwner;
        synchronized (lifecycleLock) {
            if (closed) {
                closeOwner = false;
                sessions = List.copyOf(liveSessions);
                sessions.forEach(session -> session.assertCanStop("close"));
                transitions = List.of();
                guildIds = Set.of();
            } else {
                liveSessions.forEach(session -> session.assertCanStop("close"));
                closed = true;
                sessions = List.copyOf(liveSessions);
                transitions = List.copyOf(liveTransitions);
                guildIds = new HashSet<>();
                sessions.forEach(session -> guildIds.add(session.guildId()));
                sessions.forEach(GuildSession::stopAccepting);
                presentGuilds.clear();
                expectedUnavailableGuildIds.clear();
                deactivations.clear();
                closeOwner = true;
            }
        }
        if (!closeOwner) {
            await(closeCompleted);
            return;
        }

        try {
            transitions.forEach(this::scheduleTransition);
            sessions.forEach(GuildSession::awaitDrained);
            transitions.forEach(Transition::awaitCompleted);
            guildIds.forEach(catalogueCache::remove);
            executor.shutdownNow();
            retryScheduler.shutdownNow();
            synchronized (lifecycleLock) {
                liveSessions.clear();
                liveTransitions.clear();
                guildLocks.clear();
            }
        } finally {
            closeCompleted.countDown();
        }
    }

    private GuildSession admitGuild(Guild guild) {
        String guildId = guild.getId();
        GuildSession previous;
        GuildSession session;
        Transition predecessor = null;
        synchronized (lifecycleLock) {
            if (closed) {
                return null;
            }
            previous = presentGuilds.get(guildId);
            if (previous != null) {
                previous.assertCanStop("startGuild");
                previous.stopAccepting();
                predecessor = new Transition(guildId, previous);
                liveTransitions.add(predecessor);
            } else {
                Deactivation deactivation = deactivations.remove(guildId);
                if (deactivation != null) {
                    predecessor = deactivation.transition();
                }
            }
            session = new GuildSession(guildId, ++nextEpoch, guildLockLocked(guildId), predecessor);
            presentGuilds.put(guildId, session);
            liveSessions.add(session);
            expectedUnavailableGuildIds.remove(guildId);
            deactivations.remove(guildId);
        }
        catalogueCache.remove(guildId);
        if (predecessor != null) {
            scheduleTransition(predecessor);
        }
        return session;
    }

    private void synchronizeGuild(
            Guild guild,
            GuildSession session,
            boolean refreshEmojis,
            boolean forceReadySignal
    ) {
        boolean signalReady;
        try (Admission ignored = session.admit()) {
            synchronized (session.guildLock()) {
                botApiService.upsertGuild(guild);
                if (refreshEmojis) {
                    botApiService.refreshGuildEmojis(guild);
                }
                CatalogueEnvelope next = requireValidCatalogue(botApiService.loadCatalogue(session.guildId()));
                CatalogueEnvelope previous = catalogueCache.put(session.guildId(), next);
                session.markCatalogueLoaded();
                logValidationChange(session.guildId(), previous, next);
                signalReady = isReady(next) && (forceReadySignal || !isReady(previous));
            }
        }
        if (signalReady) {
            submitReadySignal(guild, session);
        }
    }

    private void submitReadySignal(Guild guild, GuildSession session) {
        submit("signalement du catalogue prêt", session.guildId(), () -> {
            try (Admission ignored = session.admit()) {
                readyHandler.accept(guild);
            } catch (GuildNotReadyException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                logger.warn("Impossible de traiter le catalogue prêt de la guilde {}", session.guildId(), exception);
            }
        });
    }

    private void refreshGuildEmojisLocked(Guild guild, GuildSession session) {
        boolean signalReady = false;
        try (Admission ignored = session.admit()) {
            synchronized (session.guildLock()) {
                try {
                    botApiService.refreshGuildEmojis(guild);
                    return;
                } catch (ApiException exception) {
                    if (!isRecoverable(exception)) {
                        throw exception;
                    }
                }

                botApiService.upsertGuild(guild);
                CatalogueEnvelope next = requireValidCatalogue(botApiService.loadCatalogue(session.guildId()));
                CatalogueEnvelope previous = catalogueCache.put(session.guildId(), next);
                session.markCatalogueLoaded();
                logValidationChange(session.guildId(), previous, next);
                signalReady = isReady(next) && !isReady(previous);
                botApiService.refreshGuildEmojis(guild);
            }
        }
        if (signalReady) {
            submitReadySignal(guild, session);
        }
    }

    private void submitRefresh(Guild guild, GuildSession session, long refreshOwner) {
        if (!isOpen()) {
            session.abortRefresh(refreshOwner);
            return;
        }
        try {
            executor.execute(() -> runRefreshes(guild, session, refreshOwner));
        } catch (RejectedExecutionException exception) {
            session.abortRefresh(refreshOwner);
            if (isOpen()) {
                logger.warn("Impossible de planifier le rafraîchissement de la guilde {}",
                        session.guildId(), exception);
            }
        }
    }

    private void runRefreshes(Guild guild, GuildSession session, long refreshOwner) {
        try {
            boolean trailing;
            do {
                if (!isOpen()) {
                    return;
                }
                try {
                    synchronizeGuild(guild, session, false, false);
                } catch (GuildNotReadyException exception) {
                    logger.debug("Rafraîchissement ignoré pour la guilde non prête {}", session.guildId());
                } catch (RuntimeException exception) {
                    logger.warn("Échec du rafraîchissement du catalogue de la guilde {}",
                            session.guildId(), exception);
                }
                trailing = session.completeRefresh(refreshOwner);
            } while (trailing);
        } finally {
            session.abortRefresh(refreshOwner);
        }
    }

    private void submitDeactivation(Deactivation deactivation, int attemptIndex) {
        submit("désactivation", deactivation.guildId(), () -> deactivateGuild(deactivation, attemptIndex));
    }

    private void scheduleTransition(Transition transition) {
        GuildSession previous = transition.previous();
        if (previous != null && previous.predecessor() != null) {
            scheduleTransition(previous.predecessor());
        }
        if (!transition.claimScheduling()) {
            transition.awaitSchedulingCompleted();
            return;
        }
        try {
            executor.execute(() -> completeTransition(transition));
        } catch (RejectedExecutionException exception) {
            catalogueCache.remove(transition.guildId());
            retireTransition(transition);
            transition.complete();
            if (isOpen()) {
                logger.warn("Impossible de planifier le nettoyage de la guilde {}",
                        transition.guildId(), exception);
            }
        } finally {
            transition.completeScheduling();
        }
    }

    private void completeTransition(Transition transition) {
        try {
            GuildSession previous = transition.previous();
            if (previous != null) {
                previous.awaitPredecessor();
                previous.awaitDrained();
            }
            try {
                reconciliationCanceller.accept(transition.guildId());
            } catch (RuntimeException exception) {
                logger.warn("Impossible d'annuler la réconciliation de la guilde {}",
                        transition.guildId(), exception);
            }
            // An admitted operation may have repopulated the cache after the public transition.
            catalogueCache.remove(transition.guildId());
            Deactivation deactivation = transition.deactivation();
            if (deactivation != null && isCurrent(deactivation)) {
                deactivateGuild(deactivation, 0);
            }
        } finally {
            retireTransition(transition);
            transition.complete();
        }
    }

    private void retireTransition(Transition transition) {
        synchronized (lifecycleLock) {
            liveTransitions.remove(transition);
            if (transition.previous() != null) {
                liveSessions.remove(transition.previous());
            }
        }
    }

    private void deactivateGuild(Deactivation deactivation, int attemptIndex) {
        synchronized (deactivation.guildLock()) {
            if (!isCurrent(deactivation)) {
                return;
            }
            try {
                botApiService.deactivateGuild(deactivation.guildId());
                completeDeactivation(deactivation);
                return;
            } catch (ApiException exception) {
                if (exception.statusCode() == 404 && "server_not_found".equals(exception.errorCode())) {
                    completeDeactivation(deactivation);
                    return;
                }
                scheduleRetry(deactivation, attemptIndex, exception);
            } catch (RuntimeException exception) {
                scheduleRetry(deactivation, attemptIndex, exception);
            }
        }
    }

    private void scheduleRetry(Deactivation deactivation, int attemptIndex, RuntimeException failure) {
        int nextAttempt = attemptIndex + 1;
        if (nextAttempt >= DEACTIVATION_ATTEMPT_SECONDS.length) {
            completeDeactivation(deactivation);
            logger.error("Impossible de désactiver la guilde {} après {} tentatives",
                    deactivation.guildId(), DEACTIVATION_ATTEMPT_SECONDS.length, failure);
            return;
        }

        long targetNanos = deactivation.departureNanos()
                + TimeUnit.SECONDS.toNanos(DEACTIVATION_ATTEMPT_SECONDS[nextAttempt]);
        long delayNanos = Math.max(0L, targetNanos - nanoTime.getAsLong());
        try {
            retryScheduler.schedule(
                    () -> {
                        if (isCurrent(deactivation)) {
                            submitDeactivation(deactivation, nextAttempt);
                        }
                    },
                    delayNanos,
                    TimeUnit.NANOSECONDS
            );
        } catch (RejectedExecutionException exception) {
            if (isOpen()) {
                logger.warn("Impossible de planifier la désactivation de la guilde {}", deactivation.guildId(), exception);
            }
        }
    }

    private void submit(String action, String guildId, Runnable operation) {
        if (!isOpen()) {
            return;
        }
        try {
            executor.execute(() -> {
                if (!isOpen()) {
                    return;
                }
                try {
                    operation.run();
                } catch (GuildNotReadyException exception) {
                    logger.debug("Action {} ignorée pour la guilde non prête {}", action, guildId);
                } catch (RuntimeException exception) {
                    logger.warn("Échec de l'action {} pour la guilde {}", action, guildId, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            if (isOpen()) {
                logger.warn("Impossible de planifier l'action {} pour la guilde {}", action, guildId, exception);
            }
        }
    }

    private GuildSession currentSession(String guildId) {
        synchronized (lifecycleLock) {
            return closed ? null : presentGuilds.get(guildId);
        }
    }

    private GuildSession requireCurrentSession(String guildId) {
        GuildSession session = currentSession(guildId);
        if (session == null) {
            throw new GuildNotReadyException(guildId);
        }
        return session;
    }

    private void recoverSynchronously(Guild guild, GuildSession session) {
        try (Admission ignored = session.admit()) {
            synchronized (session.guildLock()) {
                botApiService.upsertGuild(guild);
                CatalogueEnvelope next = requireValidCatalogue(botApiService.loadCatalogue(session.guildId()));
                catalogueCache.put(session.guildId(), next);
                session.markCatalogueLoaded();
                catalogueCache.requireReady(session.guildId());
            }
        }
    }

    private <T> T executeAdmitted(GuildSession session, Supplier<T> operation) {
        try (Admission ignored = session.admit()) {
            if (!session.canObserveCatalogue()) {
                throw new GuildNotReadyException(session.guildId());
            }
            catalogueCache.requireReady(session.guildId());
            return operation.get();
        }
    }

    private void await(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isCurrent(Deactivation deactivation) {
        synchronized (lifecycleLock) {
            return !closed
                    && !presentGuilds.containsKey(deactivation.guildId())
                    && deactivations.get(deactivation.guildId()) == deactivation;
        }
    }

    private void completeDeactivation(Deactivation deactivation) {
        synchronized (lifecycleLock) {
            deactivations.remove(deactivation.guildId(), deactivation);
        }
    }

    private Object guildLockLocked(String guildId) {
        return guildLocks.computeIfAbsent(guildId, ignored -> new Object());
    }

    private boolean isOpen() {
        synchronized (lifecycleLock) {
            return !closed;
        }
    }

    private boolean isRecoverable(ApiException exception) {
        return exception.statusCode() == 409 && "server_inactive".equals(exception.errorCode())
                || exception.statusCode() == 404 && "server_not_found".equals(exception.errorCode());
    }

    private CatalogueEnvelope requireValidCatalogue(CatalogueEnvelope envelope) {
        if (envelope == null || envelope.server() == null || envelope.validation() == null
                || envelope.validation().errors() == null || envelope.validation().warnings() == null
                || envelope.catalogue() == null || hasNullCatalogueList(envelope.catalogue())) {
            throw new IllegalArgumentException("Catalogue API incomplet");
        }
        return envelope;
    }

    private boolean hasNullCatalogueList(ApiCatalogue catalogue) {
        return catalogue.ranks() == null || catalogue.roles() == null
                || catalogue.stats() == null || catalogue.elements() == null;
    }

    private boolean isReady(CatalogueEnvelope envelope) {
        return envelope != null && envelope.validation() != null && envelope.validation().ready();
    }

    private void logValidationChange(String guildId, CatalogueEnvelope previous, CatalogueEnvelope next) {
        ApiCatalogueValidation previousValidation = previous != null ? previous.validation() : null;
        ApiCatalogueValidation nextValidation = next.validation();
        if (previousValidation != null
                && Objects.equals(previousValidation.errors(), nextValidation.errors())
                && Objects.equals(previousValidation.warnings(), nextValidation.warnings())) {
            return;
        }
        logger.info("Validation du catalogue {} : ready={}, errors={}, warnings={}",
                guildId, nextValidation.ready(), nextValidation.errors(), nextValidation.warnings());
    }

    private static final class GuildSession {
        private final String guildId;
        private final long epoch;
        private final Object guildLock;
        private final Transition predecessor;
        private final ReentrantReadWriteLock admissionGate = new ReentrantReadWriteLock(true);
        private volatile boolean accepting = true;
        private volatile boolean catalogueLoaded;
        private final RefreshState refreshState = new RefreshState();

        private GuildSession(String guildId, long epoch, Object guildLock, Transition predecessor) {
            this.guildId = guildId;
            this.epoch = epoch;
            this.guildLock = guildLock;
            this.predecessor = predecessor;
        }

        private String guildId() {
            return guildId;
        }

        @SuppressWarnings("unused")
        private long epoch() {
            return epoch;
        }

        private Object guildLock() {
            return guildLock;
        }

        private Transition predecessor() {
            return predecessor;
        }

        private Admission admit() {
            awaitPredecessor();
            Lock readLock = admissionGate.readLock();
            readLock.lock();
            if (!accepting) {
                readLock.unlock();
                throw new GuildNotReadyException(guildId);
            }
            return new Admission(readLock);
        }

        private synchronized void stopAccepting() {
            accepting = false;
            refreshState.stop();
        }

        private long requestRefresh() {
            return refreshState.request(accepting);
        }

        private boolean completeRefresh(long refreshOwner) {
            return refreshState.complete(refreshOwner, accepting);
        }

        private void abortRefresh(long refreshOwner) {
            refreshState.abort(refreshOwner);
        }

        private boolean canObserveCatalogue() {
            return accepting && catalogueLoaded && (predecessor == null || predecessor.isCompleted());
        }

        private void markCatalogueLoaded() {
            catalogueLoaded = true;
        }

        private void awaitPredecessor() {
            if (predecessor != null) {
                predecessor.awaitCompleted();
            }
        }

        private void awaitDrained() {
            assertCanStop("attente de fin");
            Lock writeLock = admissionGate.writeLock();
            writeLock.lock();
            writeLock.unlock();
        }

        private void assertCanStop(String action) {
            if (admissionGate.getReadHoldCount() > 0) {
                throw new IllegalStateException(action
                        + " ne peut pas être appelé depuis une action admise de la guilde " + guildId);
            }
        }
    }

    static final class RefreshState {
        private long nextOwner;
        private long activeOwner;
        private boolean trailing;

        synchronized long request(boolean accepting) {
            if (!accepting) {
                return 0L;
            }
            if (activeOwner != 0L) {
                trailing = true;
                return 0L;
            }
            nextOwner++;
            if (nextOwner == 0L) {
                nextOwner++;
            }
            activeOwner = nextOwner;
            return activeOwner;
        }

        synchronized boolean complete(long owner, boolean accepting) {
            if (activeOwner != owner) {
                return false;
            }
            if (accepting && trailing) {
                trailing = false;
                return true;
            }
            activeOwner = 0L;
            trailing = false;
            return false;
        }

        synchronized void abort(long owner) {
            if (activeOwner == owner) {
                activeOwner = 0L;
                trailing = false;
            }
        }

        synchronized void stop() {
            trailing = false;
        }
    }

    private static final class Admission implements AutoCloseable {
        private final Lock lock;
        private boolean closed;

        private Admission(Lock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                lock.unlock();
            }
        }
    }

    private static final class Transition {
        private final String guildId;
        private final GuildSession previous;
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicBoolean schedulingClaimed = new AtomicBoolean();
        private final CountDownLatch schedulingCompleted = new CountDownLatch(1);
        private volatile Deactivation deactivation;

        private Transition(String guildId, GuildSession previous) {
            this.guildId = guildId;
            this.previous = previous;
        }

        private String guildId() {
            return guildId;
        }

        private GuildSession previous() {
            return previous;
        }

        private void setDeactivation(Deactivation deactivation) {
            this.deactivation = deactivation;
        }

        private Deactivation deactivation() {
            return deactivation;
        }

        private boolean claimScheduling() {
            return schedulingClaimed.compareAndSet(false, true);
        }

        private void completeScheduling() {
            schedulingCompleted.countDown();
        }

        private void awaitSchedulingCompleted() {
            awaitLatch(schedulingCompleted);
        }

        private boolean isCompleted() {
            return completed.getCount() == 0;
        }

        private void complete() {
            completed.countDown();
        }

        private void awaitCompleted() {
            awaitLatch(completed);
        }

        private static void awaitLatch(CountDownLatch latch) {
            boolean interrupted = false;
            while (true) {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record Deactivation(
            String guildId,
            long departureNanos,
            Object guildLock,
            Transition transition
    ) {
    }
}
