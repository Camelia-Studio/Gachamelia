package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CatalogueRefreshScheduler implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(CatalogueRefreshScheduler.class);

    private final GuildRuntimeCoordinator coordinator;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public CatalogueRefreshScheduler(GuildRuntimeCoordinator coordinator, RuntimeConfiguration configuration) {
        this(coordinator, configuration.catalogueRefreshInterval(), Executors.newSingleThreadScheduledExecutor());
    }

    CatalogueRefreshScheduler(
            GuildRuntimeCoordinator coordinator,
            Duration interval,
            ScheduledExecutorService executor
    ) {
        this.coordinator = Objects.requireNonNull(coordinator);
        this.interval = Objects.requireNonNull(interval);
        this.executor = Objects.requireNonNull(executor);
    }

    public void start(JDA jda) {
        Objects.requireNonNull(jda);
        if (closed.get()) {
            logger.warn("CatalogueRefreshScheduler ferme, demarrage ignore");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            logger.warn("CatalogueRefreshScheduler deja demarre, nouvelle planification ignoree");
            return;
        }

        long minutes = interval.toMinutes();
        if (minutes <= 0) {
            started.set(false);
            throw new IllegalArgumentException("Catalogue refresh interval must be at least one minute");
        }

        try {
            executor.scheduleAtFixedRate(
                    () -> jda.getGuilds().forEach(coordinator::refreshGuild),
                    minutes,
                    minutes,
                    TimeUnit.MINUTES
            );
        } catch (RuntimeException | Error exception) {
            started.set(false);
            throw exception;
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
