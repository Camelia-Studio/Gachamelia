package org.camelia.studio.gachamelia;

import net.dv8tion.jda.api.JDA;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GachameliaLifecycleTest {
    @Test
    void shutdownClosesSchedulersBeforeJdaInStartupOrder() {
        List<String> calls = new ArrayList<>();
        RecordingBotEmojiScheduler botEmojiScheduler = new RecordingBotEmojiScheduler(calls);
        RecordingGuildEmojiRefreshDebouncer emojiRefreshDebouncer = new RecordingGuildEmojiRefreshDebouncer(calls);
        JDA jda = RuntimeListenersAndCommandsTestSupport.jdaWithShutdownRecorder(calls);

        Gachamelia.shutdown(botEmojiScheduler, emojiRefreshDebouncer, jda);

        assertThat(calls).containsExactly("botEmojiScheduler.close", "emojiRefreshDebouncer.close", "jda.shutdown");
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

    private static final class RecordingGuildEmojiRefreshDebouncer extends GuildEmojiRefreshDebouncer {
        private final List<String> calls;

        private RecordingGuildEmojiRefreshDebouncer(List<String> calls) {
            super(null, new EmojiSnapshotService(), Duration.ZERO);
            this.calls = calls;
        }

        @Override
        public void close() {
            calls.add("emojiRefreshDebouncer.close");
        }
    }
}
