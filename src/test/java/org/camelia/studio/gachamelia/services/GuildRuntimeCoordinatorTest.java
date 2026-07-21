package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiServerSettings;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuildRuntimeCoordinatorTest {
    @Test
    void startGuildSynchronizesInOrderAndSignalsReady() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        ManualExecutor executor = new ManualExecutor();
        TestScheduler scheduler = new TestScheduler();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, scheduler);
        List<String> readyGuilds = new ArrayList<>();
        coordinator.setReadyHandler(guild -> readyGuilds.add(guild.getId()));

        coordinator.startGuild(guild("guild-1"));
        assertThat(api.calls).isEmpty();
        executor.runAll();

        assertThat(api.calls).containsExactly("upsert:guild-1", "emojis:guild-1", "catalogue:guild-1");
        assertThat(cache.find("guild-1")).containsSame(api.currentCatalogue);
        assertThat(readyGuilds).containsExactly("guild-1");
    }

    @Test
    void refreshSignalsOnlyFalseToTrueAndReplacesReadyWithUnready() {
        RecordingBotApiService api = new RecordingBotApiService(unreadyEnvelope());
        api.catalogues.addAll(List.of(unreadyEnvelope(), readyEnvelope(), readyEnvelope(), unreadyEnvelope()));
        GuildCatalogueCache cache = new GuildCatalogueCache();
        DirectExecutor executor = new DirectExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        List<String> readyGuilds = new ArrayList<>();
        coordinator.setReadyHandler(guild -> readyGuilds.add(guild.getId()));
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        coordinator.refreshGuild(guild);
        coordinator.refreshGuild(guild);
        coordinator.refreshGuild(guild);

        assertThat(readyGuilds).containsExactly("guild-1");
        assertThat(cache.find("guild-1")).containsSame(api.currentCatalogue);
        assertThat(cache.findReady("guild-1")).isEmpty();
    }

    @Test
    void recoverySignalsReadyEvenWhenPreviousCatalogueWasReady() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        DirectExecutor executor = new DirectExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        List<String> readyGuilds = new ArrayList<>();
        coordinator.setReadyHandler(guild -> readyGuilds.add(guild.getId()));
        Guild guild = guild("guild-1");
        coordinator.startGuild(guild);
        readyGuilds.clear();

        coordinator.recoverGuild(guild);

        assertThat(readyGuilds).containsExactly("guild-1");
    }

    @Test
    void failedRefreshPreservesLastValidCatalogue() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        DirectExecutor executor = new DirectExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler());
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        CatalogueEnvelope previous = cache.require("guild-1");
        api.loadFailures.add(new ApiException(502, "request_failed", "offline"));
        coordinator.refreshGuild(guild);

        assertThat(cache.find("guild-1")).containsSame(previous);
    }

    @Test
    void executeRuntimeRecoversInactiveServerAndRetriesExactlyOnce() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        api.calls.clear();
        AtomicInteger attempts = new AtomicInteger();

        String result = coordinator.executeRuntime(guild("guild-1"), () -> {
            if (attempts.getAndIncrement() == 0) {
                throw new ApiException(409, "server_inactive", "inactive");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
        assertThat(api.calls).containsExactly("upsert:guild-1", "catalogue:guild-1");
    }

    @Test
    void executeRuntimeRecoversMissingServerButPropagatesSecondFailure() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> coordinator.executeRuntime(guild("guild-1"), () -> {
            attempts.incrementAndGet();
            throw new ApiException(404, "server_not_found", "missing");
        })).isInstanceOf(ApiException.class).hasMessageContaining("missing");

        assertThat(attempts).hasValue(2);
    }

    @Test
    void executeRuntimeDoesNotRetryWhenRecoveredCatalogueIsUnready() {
        RecordingBotApiService api = new RecordingBotApiService(unreadyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        cache.put("guild-1", readyEnvelope());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> coordinator.executeRuntime(guild("guild-1"), () -> {
            attempts.incrementAndGet();
            throw new ApiException(409, "server_inactive", "inactive");
        })).isInstanceOf(GuildNotReadyException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void emojiRefreshRecoversInactiveServerWithoutRequiringReadyCatalogue() {
        RecordingBotApiService api = new RecordingBotApiService(unreadyEnvelope());
        api.emojiFailures.add(new ApiException(409, "server_inactive", "inactive"));
        GuildCatalogueCache cache = new GuildCatalogueCache();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, new DirectExecutor(), new TestScheduler());
        coordinator.startGuild(guild("guild-1"));
        api.calls.clear();
        api.emojiFailures.add(new ApiException(409, "server_inactive", "inactive"));

        coordinator.refreshGuildEmojis(guild("guild-1"));

        assertThat(api.calls).containsExactly(
                "emojis:guild-1", "upsert:guild-1", "catalogue:guild-1", "emojis:guild-1"
        );
        assertThat(cache.find("guild-1")).containsSame(api.currentCatalogue);
    }

    @Test
    void leaveRemovesLocalStateCancelsReconciliationAndDeactivates() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", readyEnvelope());
        ManualExecutor executor = new ManualExecutor();
        List<String> cancelled = new ArrayList<>();
        GuildRuntimeCoordinator coordinator = coordinator(api, cache, executor, new TestScheduler(), cancelled);
        coordinator.startGuild(guild("guild-1"));
        executor.runAll();
        api.calls.clear();

        coordinator.leaveGuild("guild-1");

        assertThat(cache.find("guild-1")).isEmpty();
        assertThat(cancelled).containsExactly("guild-1");
        executor.runAll();
        assertThat(api.calls).containsExactly("delete:guild-1");
    }

    @Test
    void transportFailuresRetryAtTenAndSixtySecondsFromDeparture() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        api.deactivateFailures.addAll(List.of(
                new ApiException(0, "request_failed", "offline"),
                new ApiException(0, "request_failed", "offline"),
                new ApiException(0, "request_failed", "offline")
        ));
        ManualExecutor executor = new ManualExecutor();
        MutableNanoClock clock = new MutableNanoClock();
        TestScheduler scheduler = new TestScheduler(clock);
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, scheduler, new ArrayList<>(), clock);

        coordinator.startGuild(guild("guild-1"));
        executor.runAll();
        api.calls.clear();
        coordinator.leaveGuild("guild-1");
        executor.runAll();
        assertThat(api.calls).containsExactly("delete:guild-1");

        scheduler.advanceToSeconds(9);
        executor.runAll();
        assertThat(api.calls).hasSize(1);
        scheduler.advanceToSeconds(10);
        executor.runAll();
        assertThat(api.calls).hasSize(2);
        scheduler.advanceToSeconds(59);
        executor.runAll();
        assertThat(api.calls).hasSize(2);
        scheduler.advanceToSeconds(60);
        executor.runAll();
        assertThat(api.calls).hasSize(3);
        assertThat(scheduler.pendingTaskCount()).isZero();
    }

    @Test
    void missingServerStopsDeactivationRetries() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        api.deactivateFailures.add(new ApiException(404, "server_not_found", "missing"));
        ManualExecutor executor = new ManualExecutor();
        TestScheduler scheduler = new TestScheduler();
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, scheduler);

        coordinator.startGuild(guild("guild-1"));
        executor.runAll();
        api.calls.clear();
        coordinator.leaveGuild("guild-1");
        executor.runAll();

        assertThat(api.calls).containsExactly("delete:guild-1");
        assertThat(scheduler.pendingTaskCount()).isZero();
    }

    @Test
    void rejoinCancelsPendingDeleteRetry() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        api.deactivateFailures.add(new ApiException(0, "request_failed", "offline"));
        ManualExecutor executor = new ManualExecutor();
        MutableNanoClock clock = new MutableNanoClock();
        TestScheduler scheduler = new TestScheduler(clock);
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, scheduler, new ArrayList<>(), clock);
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        executor.runAll();
        coordinator.leaveGuild("guild-1");
        executor.runAll();
        assertThat(api.deleteCalls).isEqualTo(1);

        coordinator.startGuild(guild);
        executor.runAll();
        scheduler.advanceToSeconds(10);
        executor.runAll();

        assertThat(api.deleteCalls).isEqualTo(1);
        assertThat(api.calls.getLast()).isEqualTo("catalogue:guild-1");
    }

    @Test
    void refreshQueuedBeforeLeaveCannotWriteAfterDelete() {
        RecordingBotApiService api = new RecordingBotApiService(readyEnvelope());
        ManualExecutor executor = new ManualExecutor();
        GuildRuntimeCoordinator coordinator = coordinator(api, new GuildCatalogueCache(), executor, new TestScheduler());
        Guild guild = guild("guild-1");

        coordinator.startGuild(guild);
        executor.runAll();
        api.calls.clear();
        coordinator.refreshGuild(guild);
        coordinator.leaveGuild("guild-1");
        executor.runAllReversed();

        assertThat(api.calls).containsExactly("delete:guild-1");
    }

    private static GuildRuntimeCoordinator coordinator(
            RecordingBotApiService api,
            GuildCatalogueCache cache,
            ExecutorService executor,
            ScheduledExecutorService scheduler
    ) {
        return coordinator(api, cache, executor, scheduler, new ArrayList<>());
    }

    private static GuildRuntimeCoordinator coordinator(
            RecordingBotApiService api,
            GuildCatalogueCache cache,
            ExecutorService executor,
            ScheduledExecutorService scheduler,
            List<String> cancelled
    ) {
        return new GuildRuntimeCoordinator(api, cache, cancelled::add, executor, scheduler);
    }

    private static GuildRuntimeCoordinator coordinator(
            RecordingBotApiService api,
            GuildCatalogueCache cache,
            ExecutorService executor,
            ScheduledExecutorService scheduler,
            List<String> cancelled,
            LongSupplier nanoTime
    ) {
        return new GuildRuntimeCoordinator(api, cache, cancelled::add, executor, scheduler, nanoTime);
    }

    private static CatalogueEnvelope readyEnvelope() {
        return envelope(true);
    }

    private static CatalogueEnvelope unreadyEnvelope() {
        return envelope(false);
    }

    private static CatalogueEnvelope envelope(boolean ready) {
        return new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Guild", null, new ApiServerSettings(null, null, null)),
                new ApiCatalogueValidation(ready, ready ? List.of() : List.of("role_catalogue_empty"), List.of()),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
    }

    private static Guild guild(String id) {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class<?>[]{Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> "Guild";
                    case "getIconId" -> null;
                    case "getEmojis" -> List.of();
                    case "toString" -> "Guild[" + id + "]";
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static final class RecordingBotApiService extends BotApiService {
        private final List<String> calls = new ArrayList<>();
        private final Queue<CatalogueEnvelope> catalogues = new ArrayDeque<>();
        private final Queue<RuntimeException> loadFailures = new ArrayDeque<>();
        private final Queue<RuntimeException> emojiFailures = new ArrayDeque<>();
        private final Queue<RuntimeException> deactivateFailures = new ArrayDeque<>();
        private CatalogueEnvelope currentCatalogue;
        private int deleteCalls;

        private RecordingBotApiService(CatalogueEnvelope catalogue) {
            super(null, null, null);
            currentCatalogue = catalogue;
        }

        @Override
        public DiscordServerEnvelope upsertGuild(Guild guild) {
            calls.add("upsert:" + guild.getId());
            return new DiscordServerEnvelope(currentCatalogue.server());
        }

        @Override
        public CatalogueEnvelope loadCatalogue(String guildId) {
            calls.add("catalogue:" + guildId);
            if (!loadFailures.isEmpty()) {
                throw loadFailures.remove();
            }
            if (!catalogues.isEmpty()) {
                currentCatalogue = catalogues.remove();
            }
            return currentCatalogue;
        }

        @Override
        public EmojiSnapshotResponse refreshGuildEmojis(Guild guild) {
            calls.add("emojis:" + guild.getId());
            if (!emojiFailures.isEmpty()) {
                throw emojiFailures.remove();
            }
            return new EmojiSnapshotResponse(new EmojiSnapshotResponse.EmojiCache("server", guild.getId(), 0, 0));
        }

        @Override
        public DiscordServerEnvelope deactivateGuild(String guildId) {
            calls.add("delete:" + guildId);
            deleteCalls++;
            if (!deactivateFailures.isEmpty()) {
                throw deactivateFailures.remove();
            }
            return new DiscordServerEnvelope(currentCatalogue.server());
        }
    }

    private static class ManualExecutor extends AbstractExecutorService {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException("executor closed");
            }
            tasks.addLast(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.removeFirst().run();
            }
        }

        void runAllReversed() {
            while (!tasks.isEmpty()) {
                tasks.removeLast().run();
            }
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = List.copyOf(tasks);
            tasks.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }
    }

    private static final class DirectExecutor extends ManualExecutor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static final class MutableNanoClock implements LongSupplier {
        private long now;

        @Override
        public long getAsLong() {
            return now;
        }

        void setSeconds(long seconds) {
            now = TimeUnit.SECONDS.toNanos(seconds);
        }
    }

    private static final class TestScheduler extends AbstractExecutorService implements ScheduledExecutorService {
        private final MutableNanoClock clock;
        private final Queue<ScheduledEntry> tasks = new java.util.PriorityQueue<>(Comparator.comparingLong(ScheduledEntry::targetNanos));
        private boolean shutdown;

        private TestScheduler() {
            this(new MutableNanoClock());
        }

        private TestScheduler(MutableNanoClock clock) {
            this.clock = clock;
        }

        void advanceToSeconds(long seconds) {
            clock.setSeconds(seconds);
            while (!tasks.isEmpty() && tasks.peek().targetNanos() <= clock.getAsLong()) {
                tasks.remove().command().run();
            }
        }

        int pendingTaskCount() {
            return tasks.size();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            TestScheduledFuture<Void> future = new TestScheduledFuture<>();
            tasks.add(new ScheduledEntry(clock.getAsLong() + unit.toNanos(delay), () -> {
                if (!future.isCancelled()) {
                    command.run();
                    future.complete(null);
                }
            }));
            return future;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            TestScheduledFuture<V> future = new TestScheduledFuture<>();
            tasks.add(new ScheduledEntry(clock.getAsLong() + unit.toNanos(delay), () -> {
                try {
                    future.complete(callable.call());
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            }));
            return future;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            schedule(command, 0, TimeUnit.NANOSECONDS);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            tasks.clear();
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        private record ScheduledEntry(long targetNanos, Runnable command) {
        }
    }

    private static final class TestScheduledFuture<V> extends CompletableFuture<V> implements ScheduledFuture<V> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }
    }
}
