package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class GuildEmojiRefreshDebouncer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GuildEmojiRefreshDebouncer.class);

    private final Consumer<Guild> refreshAction;
    private final Duration delay;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, GuildRefreshState> states = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public GuildEmojiRefreshDebouncer(Consumer<Guild> refreshAction, Duration delay) {
        this.refreshAction = Objects.requireNonNull(refreshAction);
        this.delay = Objects.requireNonNull(delay);
    }

    public void requestRefresh(Guild guild) {
        if (closed.get()) {
            return;
        }
        states.compute(guild.getId(), (guildId, existingState) -> {
            if (closed.get()) {
                return null;
            }
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
        if (closed.get()) {
            state.trailingRequested = false;
            return;
        }
        if (state.pendingFuture != null) {
            state.pendingFuture.cancel(false);
        }

        try {
            state.pendingFuture = executor.schedule(() -> runRefresh(guildId, state), delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            state.trailingRequested = false;
        }
    }

    private void runRefresh(String guildId, GuildRefreshState state) {
        Guild guild;
        synchronized (state) {
            state.pendingFuture = null;
            guild = state.latestGuild;
            if (guild == null || closed.get()) {
                state.trailingRequested = false;
                cleanupState(guildId, state);
                return;
            }
            state.inFlight = true;
        }

        try {
            refreshAction.accept(guild);
        } catch (Exception exception) {
            logger.warn("Impossible de rafraîchir les emojis du serveur {}", guild.getId(), exception);
        } finally {
            synchronized (state) {
                state.inFlight = false;
                if (!closed.get() && state.trailingRequested) {
                    state.trailingRequested = false;
                    scheduleLocked(guildId, state);
                } else {
                    state.trailingRequested = false;
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
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow();
            states.clear();
        }
    }

    private static final class GuildRefreshState {
        private Guild latestGuild;
        private ScheduledFuture<?> pendingFuture;
        private boolean inFlight;
        private boolean trailingRequested;
    }
}
