package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.JDA;
import org.camelia.studio.gachamelia.api.GachameliaApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotEmojiScheduler implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(BotEmojiScheduler.class);

    private final GachameliaApiClient apiClient;
    private final EmojiSnapshotService snapshotService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    public BotEmojiScheduler(GachameliaApiClient apiClient, EmojiSnapshotService snapshotService) {
        this.apiClient = apiClient;
        this.snapshotService = snapshotService;
    }

    public void start(JDA jda) {
        refreshBotEmojis(jda);
        executor.scheduleAtFixedRate(() -> refreshBotEmojis(jda), 1, 1, TimeUnit.HOURS);
    }

    public void refreshBotEmojis(JDA jda) {
        jda.retrieveApplicationEmojis().queue(
                emojis -> apiClient.refreshEmojis(snapshotService.botSnapshot(emojis)),
                error -> logger.warn("Impossible de rafraîchir les emojis bot", error)
        );
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
