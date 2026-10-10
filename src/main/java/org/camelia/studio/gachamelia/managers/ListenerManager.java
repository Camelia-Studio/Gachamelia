package org.camelia.studio.gachamelia.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.listeners.GuildEmojiListener;
import org.camelia.studio.gachamelia.listeners.GuildLifecycleListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberJoinListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberLeaveListener;
import org.camelia.studio.gachamelia.listeners.GuildMemberRoleChangeListener;
import org.camelia.studio.gachamelia.listeners.SlashCommandListener;
import org.camelia.studio.gachamelia.listeners.MessageXpListener;
import org.camelia.studio.gachamelia.services.CatalogueMessageService;
import org.camelia.studio.gachamelia.services.GuildEmojiRefreshDebouncer;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.camelia.studio.gachamelia.services.VoiceXpScheduler;
import org.camelia.studio.gachamelia.services.XpAwardService;
import org.camelia.studio.gachamelia.utils.RuntimeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ListenerManager {
    private final CommandManager commandManager;
    private final List<ListenerAdapter> listener;
    private final Logger logger = LoggerFactory.getLogger(ListenerManager.class.getName());
    private final VoiceXpScheduler voiceXpScheduler;

    public ListenerManager(
            CommandManager commandManager,
            BotApiService botApiService,
            CatalogueMessageService messageService,
            GuildEmojiRefreshDebouncer emojiRefreshDebouncer,
            GuildRuntimeCoordinator coordinator,
            RuntimeConfiguration runtimeConfiguration
    ) {
        this.commandManager = commandManager;
        listener = new ArrayList<>();
        XpAwardService xpAwardService = new XpAwardService(botApiService, coordinator);
        voiceXpScheduler = new VoiceXpScheduler(xpAwardService);

        addListener(new SlashCommandListener(commandManager));
        addListener(new GuildLifecycleListener(coordinator));
        addListener(new GuildMemberJoinListener(
                botApiService,
                coordinator,
                messageService,
                runtimeConfiguration.syncBotMembers()
        ));
        addListener(new GuildMemberLeaveListener(
                botApiService,
                coordinator,
                messageService,
                runtimeConfiguration.syncBotMembers()
        ));
        addListener(new GuildMemberRoleChangeListener(
                botApiService,
                coordinator,
                runtimeConfiguration.syncBotMembers()
        ));
        addListener(new GuildEmojiListener(emojiRefreshDebouncer));
        addListener(new MessageXpListener(xpAwardService));
    }

    public void registerListeners(JDA jda) {
        commandManager.registerCommands(jda);
        voiceXpScheduler.start(jda);

        for (ListenerAdapter listenerAdapter : listener) {
            jda.addEventListener(listenerAdapter);

            logger.info("Listener {} enregistré !", listenerAdapter.getClass().getSimpleName());
        }
    }

    private void addListener(ListenerAdapter listenerAdapter) {
        this.listener.add(listenerAdapter);
    }
}
