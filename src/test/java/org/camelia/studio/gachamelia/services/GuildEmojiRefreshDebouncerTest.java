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
import java.util.concurrent.CopyOnWriteArrayList;

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

    private static final class NoOpTransport implements ApiTransport {
        @Override
        public ApiTransportResponse send(ApiTransportRequest request) {
            throw new UnsupportedOperationException("not used in this test");
        }
    }
}
