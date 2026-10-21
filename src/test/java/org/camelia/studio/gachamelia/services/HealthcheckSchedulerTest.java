package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthcheckSchedulerTest {
    private static final URI URL = URI.create("https://status.example.test/api/push/abc");

    @Test
    void isDisabledWhenUrlIsBlank() {
        assertThat(HealthcheckScheduler.fromUrl(null, HttpClient.newHttpClient(), () -> true)).isEmpty();
        assertThat(HealthcheckScheduler.fromUrl("  ", HttpClient.newHttpClient(), () -> true)).isEmpty();
    }

    @Test
    void rejectsNonHttpUrl() {
        assertThatThrownBy(() -> HealthcheckScheduler.fromUrl("ftp://example.test", HttpClient.newHttpClient(), () -> true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pushesImmediatelyThenEvery45SecondsWhileConnected() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        List<URI> pushes = new ArrayList<>();

        try (HealthcheckScheduler scheduler = new HealthcheckScheduler(URL, HealthcheckScheduler.DEFAULT_INTERVAL, url -> {
            pushes.add(url);
            return 200;
        }, () -> true, executor)) {
            scheduler.start(jda(JDA.Status.CONNECTED));
            scheduler.start(jda(JDA.Status.CONNECTED));

            assertThat(executor.initialDelay).isZero();
            assertThat(executor.period).isEqualTo(45);
            assertThat(executor.unit).isEqualTo(TimeUnit.SECONDS);
            assertThat(executor.scheduleAtFixedRateCalls).isEqualTo(1);

            executor.runScheduledCommand();
            assertThat(pushes).containsExactly(URL);
        }

        assertThat(executor.shutdownNowCalled).isTrue();
    }

    @Test
    void skipsPushWhenJdaIsNotConnected() {
        List<URI> pushes = new ArrayList<>();
        HealthcheckScheduler scheduler = new HealthcheckScheduler(URL, Duration.ofSeconds(45), url -> {
            pushes.add(url);
            return 200;
        }, () -> true, new RecordingScheduledExecutor());

        scheduler.push(jda(JDA.Status.RECONNECT_QUEUED));

        assertThat(pushes).isEmpty();
    }

    @Test
    void skipsPushWhenApiIsUnavailable() {
        List<URI> pushes = new ArrayList<>();
        HealthcheckScheduler scheduler = new HealthcheckScheduler(URL, Duration.ofSeconds(45), url -> {
            pushes.add(url);
            return 200;
        }, () -> false, new RecordingScheduledExecutor());

        scheduler.push(jda(JDA.Status.CONNECTED));

        assertThat(pushes).isEmpty();
    }

    @Test
    void swallowsSenderFailures() {
        HealthcheckScheduler scheduler = new HealthcheckScheduler(URL, Duration.ofSeconds(45), url -> {
            throw new java.io.IOException("boom");
        }, () -> true, new RecordingScheduledExecutor());

        scheduler.push(jda(JDA.Status.CONNECTED));
    }

    private static JDA jda(JDA.Status status) {
        return (JDA) Proxy.newProxyInstance(JDA.class.getClassLoader(), new Class[]{JDA.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getStatus" -> status;
            case "toString" -> "JDA[test]";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private Runnable scheduledCommand;
        private long initialDelay = -1;
        private long period;
        private TimeUnit unit;
        private int scheduleAtFixedRateCalls;
        private boolean shutdownNowCalled;

        private void runScheduledCommand() {
            scheduledCommand.run();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            this.scheduledCommand = command;
            this.initialDelay = initialDelay;
            this.period = period;
            this.unit = unit;
            scheduleAtFixedRateCalls++;
            return new CompletedScheduledFuture<>();
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownNowCalled = true;
            return List.of();
        }

        @Override public void shutdown() { }
        @Override public boolean isShutdown() { return shutdownNowCalled; }
        @Override public boolean isTerminated() { return shutdownNowCalled; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdownNowCalled; }
        @Override public void execute(Runnable command) { command.run(); }
        @Override public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) { throw new UnsupportedOperationException(); }
    }

    private static final class CompletedScheduledFuture<V> implements ScheduledFuture<V> {
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public V get() { return null; }
        @Override public V get(long timeout, TimeUnit unit) { return null; }
    }
}
