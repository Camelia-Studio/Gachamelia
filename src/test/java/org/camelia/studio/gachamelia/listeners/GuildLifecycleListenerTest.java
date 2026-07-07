package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GuildLifecycleListenerTest {
    @Test
    void guildJoinInitializesJoinedGuild() {
        RecordingReadyListener readyListener = new RecordingReadyListener();
        GuildCatalogueCache cache = new GuildCatalogueCache();
        GuildLifecycleListener listener = new GuildLifecycleListener(readyListener, cache);
        Guild guild = guild("guild-1", "Gachamélia");

        listener.onGuildJoin(new GuildJoinEvent(jdaSelf(), 1L, guild));

        assertThat(readyListener.initializedGuildIds).containsExactly("guild-1");
    }

    @Test
    void guildJoinReturnsCleanlyWhenInitializationFails() {
        RecordingReadyListener readyListener = new RecordingReadyListener();
        readyListener.failure = new IllegalStateException("api down");
        GuildLifecycleListener listener = new GuildLifecycleListener(readyListener, new GuildCatalogueCache());
        Guild guild = guild("guild-1", "Gachamélia");

        assertThatCode(() -> listener.onGuildJoin(new GuildJoinEvent(jdaSelf(), 1L, guild)))
                .doesNotThrowAnyException();
    }

    @Test
    void guildLeaveRemovesCachedCatalogue() {
        RecordingReadyListener readyListener = new RecordingReadyListener();
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", envelope("guild-1"));
        GuildLifecycleListener listener = new GuildLifecycleListener(readyListener, cache);
        Guild guild = guild("guild-1", "Gachamélia");

        listener.onGuildLeave(new GuildLeaveEvent(jdaSelf(), 1L, guild));

        assertThat(cache.find("guild-1")).isEmpty();
    }

    @Test
    void unavailableGuildLeaveRemovesCachedCatalogue() {
        RecordingReadyListener readyListener = new RecordingReadyListener();
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("42", envelope("42"));
        GuildLifecycleListener listener = new GuildLifecycleListener(readyListener, cache);

        listener.onUnavailableGuildLeave(new UnavailableGuildLeaveEvent(jdaSelf(), 1L, 42L));

        assertThat(cache.find("42")).isEmpty();
    }

    private static CatalogueEnvelope envelope(String guildId) {
        return new CatalogueEnvelope(
                new ApiDiscordServer(guildId, "Gachamélia", null, null),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
    }

    private static Guild guild(String id, String name) {
        return proxy(
                Guild.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> id;
                    case "getName" -> name;
                    case "toString" -> "Guild[" + id + "]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );
    }

    private static JDA jdaSelf() {
        SelfUser selfUser = proxy(
                SelfUser.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAsTag" -> "Gachamelia#0001";
                    case "toString" -> "SelfUser[test]";
                    default -> defaultSnowflakeOrUnsupported(method.getName());
                }
        );

        return proxy(
                JDA.class,
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSelfUser" -> selfUser;
                    case "toString" -> "JDA[self]";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, handler);
    }

    private static Object defaultSnowflakeOrUnsupported(String methodName) {
        return switch (methodName) {
            case "getIdLong" -> 1L;
            case "hashCode" -> 1;
            case "equals" -> false;
            case "compareTo" -> 0;
            default -> throw new UnsupportedOperationException(methodName);
        };
    }

    private static final class RecordingReadyListener extends ReadyListener {
        private final List<String> initializedGuildIds = new java.util.ArrayList<>();
        private RuntimeException failure;

        private RecordingReadyListener() {
            super(null, new BotEmojiScheduler(null, new EmojiSnapshotService()));
        }

        @Override
        public void initializeGuild(Guild guild) {
            if (failure != null) {
                throw failure;
            }
            initializedGuildIds.add(guild.getId());
        }
    }
}
