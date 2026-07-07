package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import org.camelia.studio.gachamelia.api.GachameliaApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class BotEmojiScheduler implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BotEmojiScheduler.class);

    private final GachameliaApiClient apiClient;
    private final EmojiSnapshotService snapshotService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean started = new AtomicBoolean();

    public BotEmojiScheduler(GachameliaApiClient apiClient, EmojiSnapshotService snapshotService) {
        this.apiClient = apiClient;
        this.snapshotService = snapshotService;
    }

    public void start(JDA jda) {
        if (!started.compareAndSet(false, true)) {
            logger.warn("BotEmojiScheduler deja demarre, nouvelle planification ignoree");
            return;
        }

        refreshBotEmojis(jda);
        executor.scheduleAtFixedRate(() -> refreshBotEmojis(jda), 1, 1, TimeUnit.HOURS);
    }

    public void refreshBotEmojis(JDA jda) {
        jda.retrieveApplicationEmojis().queue(
                emojis -> {
                    try {
                        apiClient.refreshEmojis(snapshotService.botSnapshot(emojis));
                    } catch (Exception exception) {
                        logger.warn("Impossible de rafraîchir les emojis bot", exception);
                    }
                },
                error -> logger.warn("Impossible de rafraîchir les emojis bot", error)
        );
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
