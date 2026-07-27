package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.CatalogueRefreshScheduler;
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

class ReadyListenerTest {
    @Test
    void initializeStartsSchedulersAndDelegatesGuildsWithoutLoadingMembers() {
        RecordingRuntimeCoordinator coordinator = new RecordingRuntimeCoordinator();
        RecordingBotEmojiScheduler botEmojiScheduler = new RecordingBotEmojiScheduler();
        RecordingCatalogueRefreshScheduler catalogueScheduler = new RecordingCatalogueRefreshScheduler(coordinator);
        Guild firstGuild = guild("1");
        Guild secondGuild = guild("2");
        JDA jda = jda(List.of(firstGuild, secondGuild));

        try (coordinator; catalogueScheduler) {
            new ReadyListener(coordinator, botEmojiScheduler, catalogueScheduler).initialize(jda);

            assertThat(botEmojiScheduler.startedWith).isSameAs(jda);
            assertThat(coordinator.startedGuildIds).containsExactly("1", "2");
            assertThat(catalogueScheduler.startedWith).isSameAs(jda);
        }
    }

    private static Guild guild(String guildId) {
        return proxy(Guild.class, (proxy, method, args) -> switch (method.getName()) {
            case "getId" -> guildId;
            case "loadMembers" -> throw new AssertionError("ReadyListener must not load guild members");
            case "toString" -> "Guild[" + guildId + "]";
            default -> defaultSnowflakeOrUnsupported(method.getName());
        });
    }

    private static JDA jda(List<Guild> guilds) {
        SelfUser selfUser = proxy(SelfUser.class, (proxy, method, args) -> switch (method.getName()) {
            case "getAsTag" -> "Gachamelia#0001";
            case "toString" -> "SelfUser[test]";
            default -> defaultSnowflakeOrUnsupported(method.getName());
        });
        return proxy(JDA.class, (proxy, method, args) -> switch (method.getName()) {
            case "getGuilds" -> guilds;
            case "getSelfUser" -> selfUser;
            case "toString" -> "JDA[test]";
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

    private static final class RecordingBotEmojiScheduler extends BotEmojiScheduler {
        private JDA startedWith;

        private RecordingBotEmojiScheduler() {
            super(null, new EmojiSnapshotService());
        }

        @Override
        public void start(JDA jda) {
            startedWith = jda;
        }
    }

    private static final class RecordingCatalogueRefreshScheduler extends CatalogueRefreshScheduler {
        private JDA startedWith;

        private RecordingCatalogueRefreshScheduler(GuildRuntimeCoordinator coordinator) {
            super(coordinator, configuration());
        }

        @Override
        public void start(JDA jda) {
            startedWith = jda;
        }
    }

    private static final class RecordingRuntimeCoordinator extends GuildRuntimeCoordinator {
        private final List<String> startedGuildIds = new ArrayList<>();

        private RecordingRuntimeCoordinator() {
            super(new BotApiService(null, new EmojiSnapshotService()), new GuildCatalogueCache(), configuration(), ignored -> { });
        }

        @Override
        public void startGuild(Guild guild) {
            startedGuildIds.add(guild.getId());
        }
    }
}
