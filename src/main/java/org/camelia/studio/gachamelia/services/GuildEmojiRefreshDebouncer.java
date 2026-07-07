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
    private final Map<String, GuildRefreshState> states = new ConcurrentHashMap<>();

    public GuildEmojiRefreshDebouncer(GachameliaApiClient apiClient, EmojiSnapshotService snapshotService, Duration delay) {
        this.apiClient = apiClient;
        this.snapshotService = snapshotService;
        this.delay = delay;
    }

    public void requestRefresh(Guild guild) {
        states.compute(guild.getId(), (guildId, existingState) -> {
            GuildRefreshState state = existingState == null ? new GuildRefreshState() : existingState;
            synchronized (state) {
                state.latestGuild = guild;
                if (state.inFlight) {
                    state.trailingRequested = true;
                    return state;
                }

                scheduleLocked(guildId, state);
                return state;
            }
        });
    }

    private void scheduleLocked(String guildId, GuildRefreshState state) {
        if (state.pendingFuture != null) {
            state.pendingFuture.cancel(false);
        }

        state.pendingFuture = executor.schedule(() -> runRefresh(guildId, state), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void runRefresh(String guildId, GuildRefreshState state) {
        Guild guild;
        synchronized (state) {
            state.pendingFuture = null;
            guild = state.latestGuild;
            if (guild == null) {
                cleanupState(guildId, state);
                return;
            }
            state.inFlight = true;
        }

        try {
            apiClient.refreshEmojis(snapshotService.serverSnapshot(guild.getId(), guild.getEmojis()));
        } catch (Exception exception) {
            logger.warn("Impossible de rafraîchir les emojis du serveur {}", guild.getId(), exception);
        } finally {
            synchronized (state) {
                state.inFlight = false;
                if (state.trailingRequested) {
                    state.trailingRequested = false;
                    scheduleLocked(guildId, state);
                } else {
                    cleanupState(guildId, state);
                }
            }
        }
    }

    private void cleanupState(String guildId, GuildRefreshState state) {
        if (state.pendingFuture == null && !state.inFlight && !state.trailingRequested) {
            states.remove(guildId, state);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static final class GuildRefreshState {
        private Guild latestGuild;
        private ScheduledFuture<?> pendingFuture;
        private boolean inFlight;
        private boolean trailingRequested;
    }
}
