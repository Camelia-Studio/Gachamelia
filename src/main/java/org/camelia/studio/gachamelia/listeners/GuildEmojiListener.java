package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.events.emoji.EmojiAddedEvent;
import net.dv8tion.jda.api.events.emoji.EmojiRemovedEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateNameEvent;
import net.dv8tion.jda.api.events.emoji.update.EmojiUpdateRolesEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;

public class GuildEmojiListener extends ListenerAdapter {
    private final GuildEmojiRefreshDebouncer refreshDebouncer;

    public GuildEmojiListener(GuildEmojiRefreshDebouncer refreshDebouncer) {
        this.refreshDebouncer = refreshDebouncer;
    }

    @Override
    public void onEmojiAdded(EmojiAddedEvent event) {
        refreshDebouncer.requestRefresh(event.getGuild());
    }

    @Override
    public void onEmojiRemoved(EmojiRemovedEvent event) {
        refreshDebouncer.requestRefresh(event.getGuild());
    }

    @Override
    public void onEmojiUpdateName(EmojiUpdateNameEvent event) {
        refreshDebouncer.requestRefresh(event.getGuild());
    }

    @Override
    public void onEmojiUpdateRoles(EmojiUpdateRolesEvent event) {
        refreshDebouncer.requestRefresh(event.getGuild());
    }
}
