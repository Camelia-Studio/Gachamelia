package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class GuildEmojiRefreshDebouncerTest {
    @Test
    void delegatesTheLatestGuildToTheRefreshActionOnce() {
        List<String> refreshedGuildIds = new CopyOnWriteArrayList<>();

        try (GuildEmojiRefreshDebouncer debouncer = new GuildEmojiRefreshDebouncer(
                guild -> refreshedGuildIds.add(guild.getId()),
                Duration.ofMillis(25)
        )) {
            debouncer.requestRefresh(guild("guild-1"));
            debouncer.requestRefresh(guild("guild-1"));

            waitUntil(() -> refreshedGuildIds.size() == 1, Duration.ofSeconds(1));

            assertThat(refreshedGuildIds).containsExactly("guild-1");
        }
    }

    @Test
    void schedulesOneTrailingRefreshWithTheLatestGuildAfterAnInFlightRefresh() {
        BlockingRefreshAction refreshAction = new BlockingRefreshAction();
        Guild firstGuild = guild("guild-1");
        Guild latestGuild = guild("guild-1");
        Duration delay = Duration.ofMillis(80);

        try (GuildEmojiRefreshDebouncer debouncer = new GuildEmojiRefreshDebouncer(refreshAction, delay)) {
            debouncer.requestRefresh(firstGuild);
            assertThat(refreshAction.awaitFirstCall(Duration.ofSeconds(1))).isTrue();

            debouncer.requestRefresh(guild("guild-1"));
            debouncer.requestRefresh(latestGuild);
            Thread.sleep(delay.plusMillis(40));
            assertThat(refreshAction.refreshedGuilds).containsExactly(firstGuild);

            refreshAction.releaseFirstCall();
            waitUntil(() -> refreshAction.refreshedGuilds.size() == 2, Duration.ofSeconds(1));

            assertThat(refreshAction.refreshedGuilds).containsExactly(firstGuild, latestGuild);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test interrupted", exception);
        }
    }

    @Test
    void closesWithoutRunningPendingRefreshes() throws InterruptedException {
        List<String> refreshedGuildIds = new CopyOnWriteArrayList<>();
        GuildEmojiRefreshDebouncer debouncer = new GuildEmojiRefreshDebouncer(
                guild -> refreshedGuildIds.add(guild.getId()),
                Duration.ofMillis(200)
        );

        debouncer.requestRefresh(guild("guild-1"));
        debouncer.close();
        Thread.sleep(250);

        assertThat(refreshedGuildIds).isEmpty();
    }

    private static Guild guild(String id) {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class[]{Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "toString" -> "Guild[" + id + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
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

    private static final class BlockingRefreshAction implements Consumer<Guild> {
        private final List<Guild> refreshedGuilds = new CopyOnWriteArrayList<>();
        private final CountDownLatch firstCall = new CountDownLatch(1);
        private final CountDownLatch releaseFirstCall = new CountDownLatch(1);

        @Override
        public void accept(Guild guild) {
            refreshedGuilds.add(guild);
            if (firstCall.getCount() == 0) {
                return;
            }
            firstCall.countDown();
            try {
                if (!releaseFirstCall.await(1, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to release refresh");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test interrupted", exception);
            }
        }

        private boolean awaitFirstCall(Duration timeout) {
            try {
                return firstCall.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test interrupted", exception);
            }
        }

        private void releaseFirstCall() {
            releaseFirstCall.countDown();
        }
    }
}
