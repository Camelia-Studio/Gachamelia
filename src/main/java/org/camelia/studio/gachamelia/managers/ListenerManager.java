package org.camelia.studio.gachamelia.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.listeners.GuildEmojiListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberJoinListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberLeaveListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberRoleChangeListener;
import org.camelia.studio.gachamelia.listeners.SlashCommandListener;
import org.camelia.studio.gachamelia.services.CatalogueMessageService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ListenerManager {
    private final CommandManager commandManager;
    private final List<ListenerAdapter> listener;
    private final Logger logger = LoggerFactory.getLogger(ListenerManager.class.getName());

    public ListenerManager(
            CommandManager commandManager,
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            CatalogueMessageService messageService,
            GuildEmojiRefreshDebouncer emojiRefreshDebouncer
    ) {
        this.commandManager = commandManager;
        listener = new ArrayList<>();

        addListener(new SlashCommandListener(commandManager));
        addListener(new GuildMemberJoinListener(botApiService, catalogueCache, messageService));
        addListener(new GuildMemberLeaveListener(botApiService, catalogueCache, messageService));
        addListener(new GuildMemberRoleChangeListener(botApiService, catalogueCache));
        addListener(new GuildEmojiListener(emojiRefreshDebouncer));
    }

    public void registerListeners(JDA jda) {
        commandManager.registerCommands(jda);

        for (ListenerAdapter listenerAdapter : listener) {
            jda.addEventListener(listenerAdapter);

            logger.info("Listener {} enregistré !", listenerAdapter.getClass().getSimpleName());
        }
    }

    private void addListener(ListenerAdapter listenerAdapter) {
        this.listener.add(listenerAdapter);
    }
}
