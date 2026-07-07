package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.GachameliaApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GuildEmojiRefreshDebouncer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GuildEmojiRefreshDebouncer.class);

    private final GachameliaApiClient apiClient;
    private final EmojiSnapshotService snapshotService;
    private final Duration delay;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public GuildEmojiRefreshDebouncer(GachameliaApiClient apiClient, EmojiSnapshotService snapshotService, Duration delay) {
        this.apiClient = apiClient;
        this.snapshotService = snapshotService;
        this.delay = delay;
    }

    public void requestRefresh(Guild guild) {
        ScheduledFuture<?> existing = pending.remove(guild.getId());
        if (existing != null) {
            existing.cancel(false);
        }

        ScheduledFuture<?> scheduled = executor.schedule(() -> {
            pending.remove(guild.getId());
            try {
                apiClient.refreshEmojis(snapshotService.serverSnapshot(guild.getId(), guild.getEmojis()));
            } catch (Exception exception) {
                logger.warn("Impossible de rafraîchir les emojis du serveur {}", guild.getId(), exception);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);

        pending.put(guild.getId(), scheduled);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
