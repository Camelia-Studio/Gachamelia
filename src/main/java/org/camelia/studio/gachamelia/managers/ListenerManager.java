package org.camelia.studio.gachamelia.managers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.listeners.GuildMemberJoinListener;
import org.camelia.studio.gachamelia.listeners.SlashCommandListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ListenerManager {
    private final List<ListenerAdapter> listener;
    private final Logger logger = LoggerFactory.getLogger(ListenerManager.class.getName());
    public ListenerManager() {
        listener = new ArrayList<>();

        addListener(new SlashCommandListener());
        addListener(new GuildMemberJoinListener());
    }
    public void registerListeners(JDA jda) {
        for (ListenerAdapter listenerAdapter : listener) {
            jda.addEventListener(listenerAdapter);

            logger.info("Listener {} enregistré !", listenerAdapter.getClass().getSimpleName());
        }
    }

    private void addListener(ListenerAdapter listenerAdapter) {
        this.listener.add(listenerAdapter);
    }
}
