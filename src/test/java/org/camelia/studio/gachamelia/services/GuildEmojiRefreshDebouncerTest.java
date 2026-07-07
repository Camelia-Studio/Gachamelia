package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GuildEmojiRefreshDebouncerTest {
    @Test
    void coalescesRapidRefreshRequestsForSameGuild() {
        CapturingApiClient apiClient = new CapturingApiClient();
        EmojiSnapshotService snapshotService = new EmojiSnapshotService();

        try (GuildEmojiRefreshDebouncer debouncer = new GuildEmojiRefreshDebouncer(apiClient, snapshotService, Duration.ofMillis(25))) {
            debouncer.requestRefresh(guild("guild-1", List.of(emoji("10", "ambre", false, true))));
            debouncer.requestRefresh(guild("guild-1", List.of(emoji("20", "gachamelia", true, true))));

            waitUntil(() -> apiClient.requests.size() == 1, Duration.ofSeconds(1));

            assertThat(apiClient.requests).hasSize(1);
            EmojiSnapshotRequest request = apiClient.requests.getFirst();
            assertThat(request.source()).isEqualTo("server");
            assertThat(request.discordServerId()).isEqualTo("guild-1");
            assertThat(request.emojis()).hasSize(1);
            assertThat(request.emojis().getFirst().id()).isEqualTo("20");
            assertThat(request.emojis().getFirst().animated()).isTrue();
        }
    }

    @Test
    void coalescesTrailingRefreshRequestsWhileSameGuildRefreshIsInFlight() {
        BlockingApiClient apiClient = new BlockingApiClient("guild-1");
        EmojiSnapshotService snapshotService = new EmojiSnapshotService();
        Duration delay = Duration.ofMillis(120);

        try (GuildEmojiRefreshDebouncer debouncer = new GuildEmojiRefreshDebouncer(apiClient, snapshotService, delay)) {
            debouncer.requestRefresh(guild("guild-1", List.of(emoji("10", "ambre", false, true))));
            assertThat(apiClient.awaitEntered("guild-1", Duration.ofSeconds(1))).isTrue();

            debouncer.requestRefresh(guild("guild-1", List.of(emoji("20", "gachamelia", true, true))));
            debouncer.requestRefresh(guild("guild-1", List.of(emoji("30", "orion", false, true))));

            Thread.sleep(delay.plusMillis(40));
            assertThat(apiClient.requests).hasSize(1);

            long releasedAt = System.nanoTime();
            apiClient.release("guild-1");

            waitUntil(() -> apiClient.requests.size() == 2, Duration.ofSeconds(1));

            assertThat(apiClient.requests).hasSize(2);
            assertThat(apiClient.requests.get(0).discordServerId()).isEqualTo("guild-1");
            assertThat(apiClient.requests.get(0).emojis().getFirst().id()).isEqualTo("10");
            assertThat(apiClient.requests.get(1).discordServerId()).isEqualTo("guild-1");
            assertThat(apiClient.requests.get(1).emojis().getFirst().id()).isEqualTo("30");
            assertThat(Duration.ofNanos(apiClient.requestStartedAtNanos(1) - releasedAt))
                    .isGreaterThanOrEqualTo(delay.minusMillis(30));
            assertThat(apiClient.maxConcurrentForGuild("guild-1")).isEqualTo(1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", exception);
        }
    }

    @Test
    void keepsSeparateGuildDebounceStateIndependent() {
        BlockingApiClient apiClient = new BlockingApiClient("guild-1");
        EmojiSnapshotService snapshotService = new EmojiSnapshotService();

        try (GuildEmojiRefreshDebouncer debouncer = new GuildEmojiRefreshDebouncer(apiClient, snapshotService, Duration.ofMillis(10))) {
            debouncer.requestRefresh(guild("guild-1", List.of(emoji("10", "ambre", false, true))));
            assertThat(apiClient.awaitEntered("guild-1", Duration.ofSeconds(1))).isTrue();

            debouncer.requestRefresh(guild("guild-1", List.of(emoji("11", "ambre-plus", false, true))));
            debouncer.requestRefresh(guild("guild-2", List.of(emoji("20", "gachamelia", true, true))));
            debouncer.requestRefresh(guild("guild-2", List.of(emoji("21", "orion", false, true))));

            apiClient.release("guild-1");

            waitUntil(() -> apiClient.requests.size() == 3, Duration.ofSeconds(1));

            Map<String, List<EmojiSnapshotRequest>> requestsByGuild = apiClient.requests.stream()
                    .collect(Collectors.groupingBy(EmojiSnapshotRequest::discordServerId));

            assertThat(requestsByGuild).hasSize(2);
            assertThat(requestsByGuild.get("guild-1"))
                    .extracting(request -> request.emojis().getFirst().id())
                    .containsExactly("10", "11");
            assertThat(requestsByGuild.get("guild-2"))
                    .singleElement()
                    .extracting(request -> request.emojis().getFirst().id())
                    .isEqualTo("21");
            assertThat(apiClient.maxConcurrentForGuild("guild-1")).isEqualTo(1);
            assertThat(apiClient.maxConcurrentForGuild("guild-2")).isEqualTo(1);
        }
    }

    private static Guild guild(String id, List<? extends RichCustomEmoji> emojis) {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class[]{Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getEmojis" -> emojis;
                    case "toString" -> "Guild[" + id + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static RichCustomEmoji emoji(String id, String name, boolean animated, boolean available) {
        return (RichCustomEmoji) Proxy.newProxyInstance(
                RichCustomEmoji.class.getClassLoader(),
                new Class[]{RichCustomEmoji.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "isAnimated" -> animated;
                    case "isAvailable" -> available;
                    case "getAsMention" -> animated ? "<a:" + name + ":" + id + ">" : "<:" + name + ":" + id + ">";
                    case "getImageUrl" -> "https://cdn.test/" + id + ".webp";
                    case "toString" -> "Emoji[" + id + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static void waitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test interrupted", exception);
            }
        }
        throw new AssertionError("condition not met before timeout");
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    private static final class CapturingApiClient extends GachameliaApiClient {
        private final List<EmojiSnapshotRequest> requests = new CopyOnWriteArrayList<>();

        private CapturingApiClient() {
            super(
                    new ApiConfiguration("https://example.test", "client", "secret"),
                    new ApiTokenProvider(
                            new ApiConfiguration("https://example.test", "client", "secret"),
                            new NoOpTransport(),
                            Clock.fixed(Instant.parse("2026-07-07T10:00:00Z"), ZoneOffset.UTC)
                    ),
                    new NoOpTransport()
            );
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse refreshEmojis(EmojiSnapshotRequest request) {
            requests.add(request);
            return null;
        }
    }

    private static final class BlockingApiClient extends GachameliaApiClient {
        private final List<EmojiSnapshotRequest> requests = new CopyOnWriteArrayList<>();
        private final List<Long> requestStartTimes = new CopyOnWriteArrayList<>();
        private final Map<String, CountDownLatch> entered = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> releases = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> inFlightByGuild = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> maxConcurrentByGuild = new ConcurrentHashMap<>();
        private final String blockingGuildId;

        private BlockingApiClient(String blockingGuildId) {
            super(
                    new ApiConfiguration("https://example.test", "client", "secret"),
                    new ApiTokenProvider(
                            new ApiConfiguration("https://example.test", "client", "secret"),
                            new NoOpTransport(),
                            Clock.fixed(Instant.parse("2026-07-07T10:00:00Z"), ZoneOffset.UTC)
                    ),
                    new NoOpTransport()
            );
            this.blockingGuildId = blockingGuildId;
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse refreshEmojis(EmojiSnapshotRequest request) {
            String guildId = request.discordServerId();
            AtomicInteger inFlight = inFlightByGuild.computeIfAbsent(guildId, ignored -> new AtomicInteger());
            int current = inFlight.incrementAndGet();
            maxConcurrentByGuild.computeIfAbsent(guildId, ignored -> new AtomicInteger()).accumulateAndGet(current, Math::max);

            try {
                requestStartTimes.add(System.nanoTime());
                requests.add(request);
                if (blockingGuildId.equals(guildId)) {
                    entered.computeIfAbsent(guildId, ignored -> new CountDownLatch(1)).countDown();
                    CountDownLatch releaseLatch = releases.computeIfAbsent(guildId, ignored -> new CountDownLatch(1));
                    if (!releaseLatch.await(1, TimeUnit.SECONDS)) {
                        throw new AssertionError("timed out waiting to release " + guildId);
                    }
                }
                return null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test interrupted", exception);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        private boolean awaitEntered(String guildId, Duration timeout) {
            try {
                return entered.computeIfAbsent(guildId, ignored -> new CountDownLatch(1))
                        .await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test interrupted", exception);
            }
        }

        private void release(String guildId) {
            releases.computeIfAbsent(guildId, ignored -> new CountDownLatch(1)).countDown();
        }

        private int maxConcurrentForGuild(String guildId) {
            AtomicInteger counter = maxConcurrentByGuild.get(guildId);
            return counter == null ? 0 : counter.get();
        }

        private long requestStartedAtNanos(int index) {
            return requestStartTimes.get(index);
        }
    }

    private static final class NoOpTransport implements ApiTransport {
        @Override
        public ApiTransportResponse send(ApiTransportRequest request) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }
}
