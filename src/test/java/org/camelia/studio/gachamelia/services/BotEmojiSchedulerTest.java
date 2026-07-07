package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import net.dv8tion.jda.api.requests.RestAction;
import org.camelia.studio.gachamelia.api.ApiConfiguration;
import org.camelia.studio.gachamelia.api.ApiTokenProvider;
import org.camelia.studio.gachamelia.api.GachameliaApiClient;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotRequest;
import org.camelia.studio.gachamelia.api.http.ApiTransport;
import org.camelia.studio.gachamelia.api.http.ApiTransportRequest;
import org.camelia.studio.gachamelia.api.http.ApiTransportResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BotEmojiSchedulerTest {
    @Test
    void duplicateStartDoesNotScheduleDuplicateHourlyTasks() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        CountingApiClient apiClient = new CountingApiClient(false);

        try (BotEmojiScheduler scheduler = new BotEmojiScheduler(apiClient, new EmojiSnapshotService(), executor)) {
            scheduler.start(jdaReturning(List.of(applicationEmoji("10", "ambre", false, true))));
            scheduler.start(jdaReturning(List.of(applicationEmoji("20", "gachamelia", true, true))));

            assertThat(executor.scheduleAtFixedRateCalls()).isEqualTo(1);
            assertThat(apiClient.refreshCalls()).isEqualTo(1);
        }
    }

    @Test
    void failedSynchronousStartupDoesNotPermanentlyMarkSchedulerAsStarted() {
        RecordingScheduledExecutor executor = new RecordingScheduledExecutor();
        CountingApiClient apiClient = new CountingApiClient(true);

        try (BotEmojiScheduler scheduler = new BotEmojiScheduler(apiClient, new EmojiSnapshotService(), executor)) {
            assertThatThrownBy(() -> scheduler.start(jdaReturning(List.of(applicationEmoji("10", "ambre", false, true)))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("refresh failed");

            apiClient.failRefresh = false;

            scheduler.start(jdaReturning(List.of(applicationEmoji("20", "gachamelia", true, true))));

            assertThat(executor.scheduleAtFixedRateCalls()).isEqualTo(1);
            assertThat(apiClient.refreshCalls()).isEqualTo(2);
        }
    }

    @Test
    void refreshBotEmojisHandlesApiClientExceptionsBestEffort() {
        CountingApiClient apiClient = new CountingApiClient(true);

        try (BotEmojiScheduler scheduler = new BotEmojiScheduler(apiClient, new EmojiSnapshotService(), new RecordingScheduledExecutor())) {
            assertThatCode(() -> scheduler.refreshBotEmojis(
                    jdaReturning(List.of(applicationEmoji("10", "ambre", false, true)))
            )).doesNotThrowAnyException();

            assertThat(apiClient.refreshCalls()).isEqualTo(1);
        }
    }

    private static JDA jdaReturning(List<ApplicationEmoji> emojis) {
        RestAction<List<ApplicationEmoji>> restAction = (RestAction<List<ApplicationEmoji>>) Proxy.newProxyInstance(
                RestAction.class.getClassLoader(),
                new Class[]{RestAction.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "queue" -> {
                        Consumer<List<ApplicationEmoji>> success = (Consumer<List<ApplicationEmoji>>) args[0];
                        success.accept(emojis);
                        yield null;
                    }
                    case "submit" -> CompletableFuture.completedFuture(emojis);
                    case "complete" -> emojis;
                    case "getJDA", "setCheck" -> null;
                    case "toString" -> "ImmediateRestAction";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );

        return (JDA) Proxy.newProxyInstance(
                JDA.class.getClassLoader(),
                new Class[]{JDA.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "retrieveApplicationEmojis" -> restAction;
                    case "toString" -> "JDA[test]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static ApplicationEmoji applicationEmoji(String id, String name, boolean animated, boolean available) {
        return (ApplicationEmoji) Proxy.newProxyInstance(
                ApplicationEmoji.class.getClassLoader(),
                new Class[]{ApplicationEmoji.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "isAnimated" -> animated;
                    case "isAvailable" -> available;
                    case "getAsMention" -> animated ? "<a:" + name + ":" + id + ">" : "<:" + name + ":" + id + ">";
                    case "getImageUrl" -> "https://cdn.test/" + id + ".webp";
                    case "toString" -> "ApplicationEmoji[" + id + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class CountingApiClient extends GachameliaApiClient {
        private final AtomicInteger refreshCalls = new AtomicInteger();
        private volatile boolean failRefresh;

        private CountingApiClient(boolean failRefresh) {
            super(
                    new ApiConfiguration("https://example.test", "client", "secret"),
                    new ApiTokenProvider(
                            new ApiConfiguration("https://example.test", "client", "secret"),
                            new NoOpTransport(),
                            Clock.fixed(Instant.parse("2026-07-07T10:00:00Z"), ZoneOffset.UTC)
                    ),
                    new NoOpTransport()
            );
            this.failRefresh = failRefresh;
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse refreshEmojis(EmojiSnapshotRequest request) {
            refreshCalls.incrementAndGet();
            if (failRefresh) {
                throw new IllegalStateException("refresh failed");
            }
            return null;
        }

        private int refreshCalls() {
            return refreshCalls.get();
        }
    }

    private static final class RecordingScheduledExecutor extends AbstractExecutorService implements ScheduledExecutorService {
        private final AtomicInteger scheduleAtFixedRateCalls = new AtomicInteger();
        private volatile RuntimeException failure;
        private volatile boolean shutdown;

        private int scheduleAtFixedRateCalls() {
            return scheduleAtFixedRateCalls.get();
        }

        private void failOnSchedule(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            scheduleAtFixedRateCalls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return new CompletedScheduledFuture<>(null);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("not used in this test");
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }

    private static final class CompletedScheduledFuture<V> implements ScheduledFuture<V> {
        private final V value;

        private CompletedScheduledFuture(V value) {
            this.value = value;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public V get() {
            return value;
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            return value;
        }
    }

    private static final class NoOpTransport implements ApiTransport {
        @Override
        public ApiTransportResponse send(ApiTransportRequest request) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }
}
