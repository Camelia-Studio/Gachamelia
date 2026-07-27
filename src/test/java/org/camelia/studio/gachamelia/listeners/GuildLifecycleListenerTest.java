package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildJoinedEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuildLifecycleListenerTest {
    @Test
    void guildJoinStartsTheAvailableGuild() {
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator();
        try (coordinator) {
            new GuildLifecycleListener(coordinator).onGuildJoin(new GuildJoinEvent(jdaSelf(), 1L, guild("42")));

            assertThat(coordinator.startedGuildIds).containsExactly("42");
        }
    }

    @Test
    void unavailableGuildJoinRecordsExpectationUntilAvailability() {
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator();
        try (coordinator) {
            GuildLifecycleListener listener = new GuildLifecycleListener(coordinator);
            listener.onUnavailableGuildJoined(new UnavailableGuildJoinedEvent(jdaSelf(), 1L, 42L));
            listener.onGuildAvailable(new GuildAvailableEvent(jdaSelf(), 2L, guild("42")));

            assertThat(coordinator.expectedGuildIds).containsExactly("42");
            assertThat(coordinator.availableGuildIds).containsExactly("42");
        }
    }

    @Test
    void unavailableGuildDoesNotDeactivateIt() {
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator();
        try (coordinator) {
            new GuildLifecycleListener(coordinator).onGuildUnavailable(new GuildUnavailableEvent(jdaSelf(), 3L, guild("42")));

            assertThat(coordinator.leftGuildIds).isEmpty();
            assertThat(coordinator.startedGuildIds).isEmpty();
        }
    }

    @Test
    void bothLeaveEventsDeactivateExactlyOnce() {
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator();
        try (coordinator) {
            GuildLifecycleListener listener = new GuildLifecycleListener(coordinator);
            listener.onGuildLeave(new GuildLeaveEvent(jdaSelf(), 4L, guild("42")));
            listener.onUnavailableGuildLeave(new UnavailableGuildLeaveEvent(jdaSelf(), 5L, 43L));

            assertThat(coordinator.leftGuildIds).containsExactly("42", "43");
        }
    }

    private static Guild guild(String id) {
        return proxy(Guild.class, (proxy, method, args) -> switch (method.getName()) {
            case "getId" -> id;
            case "getName" -> "Gachamelia";
            case "toString" -> "Guild[" + id + "]";
            default -> defaultSnowflakeOrUnsupported(method.getName());
        });
    }

    private static JDA jdaSelf() {
        SelfUser selfUser = proxy(SelfUser.class, (proxy, method, args) -> switch (method.getName()) {
            case "getAsTag" -> "Gachamelia#0001";
            case "toString" -> "SelfUser[test]";
            default -> defaultSnowflakeOrUnsupported(method.getName());
        });
        return proxy(JDA.class, (proxy, method, args) -> switch (method.getName()) {
            case "getSelfUser" -> selfUser;
            case "toString" -> "JDA[self]";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static RuntimeConfiguration configuration() {
        return new RuntimeConfiguration(Duration.ofMinutes(5), 1, false);
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

    private static final class RecordingGuildRuntimeCoordinator extends GuildRuntimeCoordinator {
        private final List<String> startedGuildIds = new ArrayList<>();
        private final List<String> expectedGuildIds = new ArrayList<>();
        private final List<String> availableGuildIds = new ArrayList<>();
        private final List<String> leftGuildIds = new ArrayList<>();

        private RecordingGuildRuntimeCoordinator() {
            super(new BotApiService(null, new EmojiSnapshotService()), new GuildCatalogueCache(), configuration(), ignored -> { });
        }

        @Override
        public void startGuild(Guild guild) {
            startedGuildIds.add(guild.getId());
        }

        @Override
        public void expectUnavailableGuild(String guildId) {
            expectedGuildIds.add(guildId);
        }

        @Override
        public void guildAvailable(Guild guild) {
            availableGuildIds.add(guild.getId());
        }

        @Override
        public void leaveGuild(String guildId) {
            leftGuildIds.add(guildId);
        }
    }
}
