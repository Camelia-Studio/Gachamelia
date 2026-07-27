package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
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

class CatalogueRefreshSchedulerTest {
    @Test
    void schedulesOneRefreshPerConfiguredIntervalAndRefreshesAllGuilds() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator();
        JDA jda = jda(guild("1"), guild("2"));

        try (coordinator; CatalogueRefreshScheduler scheduler = new CatalogueRefreshScheduler(coordinator, Duration.ofMinutes(5), executor)) {
            scheduler.start(jda);
            scheduler.start(jda);

            assertThat(executor.initialDelay).isEqualTo(5);
            assertThat(executor.period).isEqualTo(5);
            assertThat(executor.unit).isEqualTo(TimeUnit.MINUTES);
            assertThat(executor.scheduleAtFixedRateCalls).isEqualTo(1);

            executor.runScheduledCommand();
            assertThat(coordinator.refreshedGuildIds).containsExactly("1", "2");
        }
    }

    @Test
    void closeStopsTheSchedulerTerminally() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator();
        CatalogueRefreshScheduler scheduler = new CatalogueRefreshScheduler(coordinator, Duration.ofMinutes(5), executor);

        try (coordinator; scheduler) {
            scheduler.start(jda(guild("1")));
        }

        assertThat(executor.shutdownNowCalled).isTrue();
    }

    @Test
    void cannotScheduleAgainAfterClose() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator();
        CatalogueRefreshScheduler scheduler = new CatalogueRefreshScheduler(coordinator, Duration.ofMinutes(5), executor);

        try (coordinator; scheduler) {
            scheduler.close();
            scheduler.start(jda(guild("1")));
        }

        assertThat(executor.scheduleAtFixedRateCalls).isZero();
    }

    private static JDA jda(Guild... guilds) {
        return proxy(JDA.class, (proxy, method, args) -> switch (method.getName()) {
            case "getGuilds" -> List.of(guilds);
            case "toString" -> "JDA[test]";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Guild guild(String id) {
        return proxy(Guild.class, (proxy, method, args) -> switch (method.getName()) {
            case "getId" -> id;
            case "toString" -> "Guild[" + id + "]";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    private static final class RecordingGuildRuntimeCoordinator extends GuildRuntimeCoordinator {
        private final List<String> refreshedGuildIds = new ArrayList<>();

        private RecordingGuildRuntimeCoordinator() {
            super(new BotApiService(null, new EmojiSnapshotService()), new GuildCatalogueCache(), new RuntimeConfiguration(Duration.ofMinutes(5), 1, false), ignored -> { });
        }

        @Override
        public void refreshGuild(Guild guild) {
            refreshedGuildIds.add(guild.getId());
        }
    }

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private Runnable scheduledCommand;
        private long initialDelay;
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
