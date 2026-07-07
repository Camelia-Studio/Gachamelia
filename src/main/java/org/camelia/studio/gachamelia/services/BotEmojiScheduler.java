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
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public BotEmojiScheduler(GachameliaApiClient apiClient, EmojiSnapshotService snapshotService) {
        this(apiClient, snapshotService, Executors.newSingleThreadScheduledExecutor());
    }

    BotEmojiScheduler(GachameliaApiClient apiClient, EmojiSnapshotService snapshotService, ScheduledExecutorService executor) {
        this.apiClient = apiClient;
        this.snapshotService = snapshotService;
        this.executor = executor;
    }

    public void start(JDA jda) {
        if (!started.compareAndSet(false, true)) {
            logger.warn("BotEmojiScheduler deja demarre, nouvelle planification ignoree");
            return;
        }

        try {
            refreshBotEmojisOrThrow(jda);
            executor.scheduleAtFixedRate(() -> refreshBotEmojis(jda), 1, 1, TimeUnit.HOURS);
        } catch (RuntimeException | Error exception) {
            started.set(false);
            throw exception;
        }
    }

    public void refreshBotEmojis(JDA jda) {
        try {
            refreshBotEmojisOrThrow(jda);
        } catch (RuntimeException exception) {
            logger.warn("Impossible de rafraîchir les emojis bot", exception);
        }
    }

    private void refreshBotEmojisOrThrow(JDA jda) {
        apiClient.refreshEmojis(snapshotService.botSnapshot(jda.retrieveApplicationEmojis().complete()));
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
