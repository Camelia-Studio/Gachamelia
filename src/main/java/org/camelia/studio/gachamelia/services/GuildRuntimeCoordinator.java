package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final Set<String> presentGuildIds = ConcurrentHashMap.newKeySet();
    private final Set<String> expectedUnavailableGuildIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Object> guildLocks = new ConcurrentHashMap<>();
    private final Map<String, Deactivation> deactivations = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
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
        if (closed.get()) {
            return;
        }
        String guildId = guild.getId();
        presentGuildIds.add(guildId);
        expectedUnavailableGuildIds.remove(guildId);
        deactivations.remove(guildId);
        submit("synchronisation initiale", guildId, () -> synchronizeGuild(guild, true, false));
    }

    public void refreshGuild(Guild guild) {
        if (closed.get() || !presentGuildIds.contains(guild.getId())) {
            return;
        }
        submit("rafraîchissement du catalogue", guild.getId(), () -> synchronizeGuild(guild, false, false));
    }

    public void recoverGuild(Guild guild) {
        if (closed.get() || !presentGuildIds.contains(guild.getId())) {
            return;
        }
        submit("récupération de la guilde", guild.getId(), () -> synchronizeGuild(guild, false, true));
    }

    public void expectUnavailableGuild(String guildId) {
        if (!closed.get()) {
            expectedUnavailableGuildIds.add(guildId);
        }
    }

    public void guildAvailable(Guild guild) {
        if (closed.get()) {
            return;
        }
        expectedUnavailableGuildIds.remove(guild.getId());
        startGuild(guild);
    }

    public void leaveGuild(String guildId) {
        if (closed.get()) {
            return;
        }
        presentGuildIds.remove(guildId);
        expectedUnavailableGuildIds.remove(guildId);
        catalogueCache.remove(guildId);
        reconciliationCanceller.accept(guildId);

        Deactivation deactivation = new Deactivation(guildId, nanoTime.getAsLong());
        deactivations.put(guildId, deactivation);
        submitDeactivation(deactivation, 0);
    }

    public Optional<CatalogueEnvelope> findReadyCatalogue(String guildId) {
        return catalogueCache.findReady(guildId);
    }

    public <T> T executeRuntime(Guild guild, Supplier<T> operation) {
        String guildId = guild.getId();
        catalogueCache.requireReady(guildId);
        try {
            return operation.get();
        } catch (ApiException exception) {
            if (!isRecoverable(exception)) {
                throw exception;
            }
        }

        synchronized (guildLock(guildId)) {
            requirePresent(guildId);
            botApiService.upsertGuild(guild);
            CatalogueEnvelope next = botApiService.loadCatalogue(guildId);
            catalogueCache.put(guildId, next);
            catalogueCache.requireReady(guildId);
            requirePresent(guildId);
            return operation.get();
        }
    }

    public void refreshGuildEmojis(Guild guild) {
        if (closed.get() || !presentGuildIds.contains(guild.getId())) {
            return;
        }
        submit("rafraîchissement des emojis", guild.getId(), () -> refreshGuildEmojisLocked(guild));
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        presentGuildIds.clear();
        expectedUnavailableGuildIds.clear();
        deactivations.clear();
        executor.shutdownNow();
        retryScheduler.shutdownNow();
    }

    private void synchronizeGuild(Guild guild, boolean refreshEmojis, boolean forceReadySignal) {
        String guildId = guild.getId();
        boolean signalReady;
        synchronized (guildLock(guildId)) {
            if (!isPresent(guildId)) {
                return;
            }
            botApiService.upsertGuild(guild);
            if (refreshEmojis) {
                botApiService.refreshGuildEmojis(guild);
            }
            CatalogueEnvelope next = botApiService.loadCatalogue(guildId);
            CatalogueEnvelope previous = catalogueCache.put(guildId, next);
            logValidationChange(guildId, previous, next);
            signalReady = isReady(next) && (forceReadySignal || !isReady(previous));
        }
        if (signalReady && isPresent(guildId)) {
            try {
                readyHandler.accept(guild);
            } catch (RuntimeException exception) {
                logger.warn("Impossible de traiter le catalogue prêt de la guilde {}", guildId, exception);
            }
        }
    }

    private void refreshGuildEmojisLocked(Guild guild) {
        String guildId = guild.getId();
        synchronized (guildLock(guildId)) {
            if (!isPresent(guildId)) {
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

            requirePresent(guildId);
            botApiService.upsertGuild(guild);
            CatalogueEnvelope next = botApiService.loadCatalogue(guildId);
            CatalogueEnvelope previous = catalogueCache.put(guildId, next);
            logValidationChange(guildId, previous, next);
            requirePresent(guildId);
            botApiService.refreshGuildEmojis(guild);
        }
    }

    private void submitDeactivation(Deactivation deactivation, int attemptIndex) {
        submit("désactivation", deactivation.guildId(), () -> deactivateGuild(deactivation, attemptIndex));
    }

    private void deactivateGuild(Deactivation deactivation, int attemptIndex) {
        String guildId = deactivation.guildId();
        synchronized (guildLock(guildId)) {
            if (!isCurrent(deactivation)) {
                return;
            }
            try {
                botApiService.deactivateGuild(guildId);
                deactivations.remove(guildId, deactivation);
                return;
            } catch (ApiException exception) {
                if (exception.statusCode() == 404 && "server_not_found".equals(exception.errorCode())) {
                    deactivations.remove(guildId, deactivation);
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
            deactivations.remove(deactivation.guildId(), deactivation);
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
            if (!closed.get()) {
                logger.warn("Impossible de planifier la désactivation de la guilde {}", deactivation.guildId(), exception);
            }
        }
    }

    private void submit(String action, String guildId, Runnable operation) {
        if (closed.get()) {
            return;
        }
        try {
            executor.execute(() -> {
                if (closed.get()) {
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
            if (!closed.get()) {
                logger.warn("Impossible de planifier l'action {} pour la guilde {}", action, guildId, exception);
            }
        }
    }

    private Object guildLock(String guildId) {
        return guildLocks.computeIfAbsent(guildId, ignored -> new Object());
    }

    private boolean isPresent(String guildId) {
        return !closed.get() && presentGuildIds.contains(guildId);
    }

    private boolean isCurrent(Deactivation deactivation) {
        return !closed.get()
                && !presentGuildIds.contains(deactivation.guildId())
                && deactivations.get(deactivation.guildId()) == deactivation;
    }

    private void requirePresent(String guildId) {
        if (!isPresent(guildId)) {
            throw new GuildNotReadyException(guildId);
        }
    }

    private boolean isRecoverable(ApiException exception) {
        return exception.statusCode() == 409 && "server_inactive".equals(exception.errorCode())
                || exception.statusCode() == 404 && "server_not_found".equals(exception.errorCode());
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

    private record Deactivation(String guildId, long departureNanos) {
    }
}
