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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    // Never acquire a guild lock or invoke callbacks/API calls while holding this lock.
    private final Object lifecycleLock = new Object();
    private final Map<String, GuildSession> presentGuilds = new HashMap<>();
    private final Set<String> expectedUnavailableGuildIds = new HashSet<>();
    private final Map<String, Object> guildLocks = new HashMap<>();
    private final Map<String, Deactivation> deactivations = new HashMap<>();
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
            submit("rafraîchissement du catalogue", session.guildId(),
                    () -> synchronizeGuild(guild, session, false, false));
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
        Deactivation deactivation;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            presentGuilds.remove(guildId);
            expectedUnavailableGuildIds.remove(guildId);
            catalogueCache.remove(guildId);
            deactivation = new Deactivation(guildId, nanoTime.getAsLong(), guildLockLocked(guildId));
            deactivations.put(guildId, deactivation);
        }

        reconciliationCanceller.accept(guildId);
        submitDeactivation(deactivation, 0);
    }

    public Optional<CatalogueEnvelope> findReadyCatalogue(String guildId) {
        synchronized (lifecycleLock) {
            if (closed) {
                return Optional.empty();
            }
        }
        return catalogueCache.findReady(guildId);
    }

    public <T> T executeRuntime(Guild guild, Supplier<T> operation) {
        String guildId = guild.getId();
        GuildSession session = requireCurrentSession(guildId);
        catalogueCache.requireReady(guildId);
        requireCurrent(session);
        try {
            return operation.get();
        } catch (ApiException exception) {
            if (!isRecoverable(exception)) {
                throw exception;
            }
        }

        synchronized (session.guildLock()) {
            requireCurrent(session);
            botApiService.upsertGuild(guild);
            CatalogueEnvelope next = requireValidCatalogue(botApiService.loadCatalogue(guildId));
            replaceCatalogue(session, next);
            catalogueCache.requireReady(guildId);
        }
        requireCurrent(session);
        return operation.get();
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
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            presentGuilds.clear();
            expectedUnavailableGuildIds.clear();
            deactivations.clear();
            guildLocks.clear();
        }
        executor.shutdownNow();
        retryScheduler.shutdownNow();
    }

    private GuildSession admitGuild(Guild guild) {
        String guildId = guild.getId();
        synchronized (lifecycleLock) {
            if (closed) {
                return null;
            }
            GuildSession session = new GuildSession(guildId, ++nextEpoch, guildLockLocked(guildId));
            presentGuilds.put(guildId, session);
            expectedUnavailableGuildIds.remove(guildId);
            deactivations.remove(guildId);
            return session;
        }
    }

    private void synchronizeGuild(
            Guild guild,
            GuildSession session,
            boolean refreshEmojis,
            boolean forceReadySignal
    ) {
        boolean signalReady;
        synchronized (session.guildLock()) {
            if (!isCurrent(session)) {
                return;
            }
            botApiService.upsertGuild(guild);
            if (!isCurrent(session)) {
                return;
            }
            if (refreshEmojis) {
                botApiService.refreshGuildEmojis(guild);
                if (!isCurrent(session)) {
                    return;
                }
            }
            CatalogueEnvelope next = requireValidCatalogue(botApiService.loadCatalogue(session.guildId()));
            CatalogueEnvelope previous;
            synchronized (lifecycleLock) {
                if (!isCurrentLocked(session)) {
                    return;
                }
                previous = catalogueCache.put(session.guildId(), next);
            }
            logValidationChange(session.guildId(), previous, next);
            signalReady = isReady(next) && (forceReadySignal || !isReady(previous));
        }
        if (signalReady) {
            submitReadySignal(guild, session);
        }
    }

    private void submitReadySignal(Guild guild, GuildSession session) {
        submit("signalement du catalogue prêt", session.guildId(), () -> {
            Consumer<Guild> handler;
            synchronized (lifecycleLock) {
                if (!isCurrentLocked(session)) {
                    return;
                }
                handler = readyHandler;
            }
            try {
                handler.accept(guild);
            } catch (RuntimeException exception) {
                logger.warn("Impossible de traiter le catalogue prêt de la guilde {}", session.guildId(), exception);
            }
        });
    }

    private void refreshGuildEmojisLocked(Guild guild, GuildSession session) {
        synchronized (session.guildLock()) {
            if (!isCurrent(session)) {
                return;
            }
            try {
                botApiService.refreshGuildEmojis(guild);
                return;
            } catch (ApiException exception) {
                if (!isRecoverable(exception)) {
                    throw exception;
                }
            }

            requireCurrent(session);
            botApiService.upsertGuild(guild);
            CatalogueEnvelope next = requireValidCatalogue(botApiService.loadCatalogue(session.guildId()));
            CatalogueEnvelope previous = replaceCatalogue(session, next);
            logValidationChange(session.guildId(), previous, next);
            requireCurrent(session);
            botApiService.refreshGuildEmojis(guild);
        }
    }

    private void submitDeactivation(Deactivation deactivation, int attemptIndex) {
        submit("désactivation", deactivation.guildId(), () -> deactivateGuild(deactivation, attemptIndex));
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

    private void requireCurrent(GuildSession session) {
        if (!isCurrent(session)) {
            throw new GuildNotReadyException(session.guildId());
        }
    }

    private CatalogueEnvelope replaceCatalogue(GuildSession session, CatalogueEnvelope next) {
        synchronized (lifecycleLock) {
            if (!isCurrentLocked(session)) {
                throw new GuildNotReadyException(session.guildId());
            }
            return catalogueCache.put(session.guildId(), next);
        }
    }

    private boolean isCurrent(GuildSession session) {
        synchronized (lifecycleLock) {
            return isCurrentLocked(session);
        }
    }

    private boolean isCurrentLocked(GuildSession session) {
        GuildSession current = presentGuilds.get(session.guildId());
        return !closed && current != null && current.epoch() == session.epoch();
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

    private record GuildSession(String guildId, long epoch, Object guildLock) {
    }

    private record Deactivation(String guildId, long departureNanos, Object guildLock) {
    }
}
