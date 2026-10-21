package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public class HealthcheckScheduler implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(HealthcheckScheduler.class);
    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(45);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @FunctionalInterface
    interface HealthcheckSender {
        int send(URI url) throws IOException, InterruptedException;
    }

    private final URI url;
    private final Duration interval;
    private final HealthcheckSender sender;
    private final BooleanSupplier apiAvailable;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public static Optional<HealthcheckScheduler> fromUrl(String rawUrl, HttpClient httpClient, BooleanSupplier apiAvailable) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }
        URI url;
        try {
            url = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("HEALTHCHECK_URL must be a valid URL", exception);
        }
        if (url.getScheme() == null || !(url.getScheme().equals("http") || url.getScheme().equals("https"))) {
            throw new IllegalArgumentException("HEALTHCHECK_URL must be an http(s) URL");
        }
        Objects.requireNonNull(httpClient);
        HealthcheckSender sender = target -> httpClient.send(
                HttpRequest.newBuilder(target).timeout(REQUEST_TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.discarding()
        ).statusCode();
        return Optional.of(new HealthcheckScheduler(url, DEFAULT_INTERVAL, sender, apiAvailable, Executors.newSingleThreadScheduledExecutor()));
    }

    HealthcheckScheduler(
            URI url,
            Duration interval,
            HealthcheckSender sender,
            BooleanSupplier apiAvailable,
            ScheduledExecutorService executor
    ) {
        this.url = Objects.requireNonNull(url);
        this.interval = Objects.requireNonNull(interval);
        this.sender = Objects.requireNonNull(sender);
        this.apiAvailable = Objects.requireNonNull(apiAvailable);
        this.executor = Objects.requireNonNull(executor);
    }

    public void start(JDA jda) {
        Objects.requireNonNull(jda);
        if (closed.get()) {
            logger.warn("HealthcheckScheduler ferme, demarrage ignore");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            logger.warn("HealthcheckScheduler deja demarre, nouvelle planification ignoree");
            return;
        }

        try {
            executor.scheduleAtFixedRate(() -> push(jda), 0, interval.toSeconds(), TimeUnit.SECONDS);
        } catch (RuntimeException | Error exception) {
            started.set(false);
            throw exception;
        }
    }

    void push(JDA jda) {
        // Pas de push si le bot ne peut pas fonctionner : la sonde détectera l'absence de signal.
        if (jda.getStatus() != JDA.Status.CONNECTED) {
            logger.warn("Healthcheck ignore, statut JDA : {}", jda.getStatus());
            return;
        }
        if (!apiAvailable.getAsBoolean()) {
            logger.warn("Healthcheck ignore, API indisponible lors du dernier appel");
            return;
        }
        try {
            int statusCode = sender.send(url);
            if (statusCode < 200 || statusCode >= 300) {
                logger.warn("Healthcheck refuse par la sonde (HTTP {})", statusCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            logger.warn("Impossible d'envoyer le healthcheck : {}", exception.getMessage());
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
        }
    }
}
