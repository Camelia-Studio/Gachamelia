package org.camelia.studio.gachamelia;

import net.dv8tion.jda.api.JDA;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.CatalogueRefreshScheduler;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.camelia.studio.gachamelia.services.MemberReconciliationService;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GachameliaLifecycleTest {
    @Test
    void shutdownClosesRuntimeServicesBeforeJdaInDependencyOrder() {
        List<String> calls = new ArrayList<>();
        RecordingGuildRuntimeCoordinator coordinator = new RecordingGuildRuntimeCoordinator(calls);
        RecordingMemberReconciliationService reconciliationService = new RecordingMemberReconciliationService(calls);
        RecordingCatalogueRefreshScheduler catalogueRefreshScheduler = new RecordingCatalogueRefreshScheduler(coordinator, calls);
        RecordingGuildEmojiRefreshDebouncer emojiRefreshDebouncer = new RecordingGuildEmojiRefreshDebouncer(calls);
        RecordingBotEmojiScheduler botEmojiScheduler = new RecordingBotEmojiScheduler(calls);
        JDA jda = RuntimeListenersAndCommandsTestSupport.jdaWithShutdownRecorder(calls);

        Gachamelia.shutdown(
                catalogueRefreshScheduler,
                emojiRefreshDebouncer,
                coordinator,
                reconciliationService,
                botEmojiScheduler,
                jda
        );

        assertThat(calls).containsExactly(
                "catalogueRefreshScheduler.close",
                "emojiRefreshDebouncer.close",
                "guildRuntimeCoordinator.close",
                "memberReconciliationService.close",
                "botEmojiScheduler.close",
                "jda.shutdown"
        );
    }

    private static RuntimeConfiguration configuration() {
        return new RuntimeConfiguration(Duration.ofMinutes(5), 1, false);
    }

    private static final class RecordingCatalogueRefreshScheduler extends CatalogueRefreshScheduler {
        private final List<String> calls;

        private RecordingCatalogueRefreshScheduler(GuildRuntimeCoordinator coordinator, List<String> calls) {
            super(coordinator, configuration());
            this.calls = calls;
        }

        @Override
        public void close() {
            calls.add("catalogueRefreshScheduler.close");
        }
    }

    private static final class RecordingGuildEmojiRefreshDebouncer extends GuildEmojiRefreshDebouncer {
        private final List<String> calls;

        private RecordingGuildEmojiRefreshDebouncer(List<String> calls) {
            super(ignored -> { }, Duration.ZERO);
            this.calls = calls;
        }

        @Override
        public void close() {
            calls.add("emojiRefreshDebouncer.close");
        }
    }

    private static final class RecordingGuildRuntimeCoordinator extends GuildRuntimeCoordinator {
        private final List<String> calls;

        private RecordingGuildRuntimeCoordinator(List<String> calls) {
            super(new BotApiService(null, new EmojiSnapshotService()), new GuildCatalogueCache(), configuration(), ignored -> { });
            this.calls = calls;
        }

        @Override
        public void close() {
            calls.add("guildRuntimeCoordinator.close");
        }
    }

    private static final class RecordingMemberReconciliationService extends MemberReconciliationService {
        private final List<String> calls;

        private RecordingMemberReconciliationService(List<String> calls) {
            super(new BotApiService(null, new EmojiSnapshotService()), new GuildCatalogueCache(), configuration(), ignored -> { });
            this.calls = calls;
        }

        @Override
        public void close() {
            calls.add("memberReconciliationService.close");
        }
    }

    private static final class RecordingBotEmojiScheduler extends BotEmojiScheduler {
        private final List<String> calls;

        private RecordingBotEmojiScheduler(List<String> calls) {
            super(null, new EmojiSnapshotService());
            this.calls = calls;
        }

        @Override
        public void close() {
            calls.add("botEmojiScheduler.close");
        }
    }
}
