package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);


    @Override
    public void onReady(ReadyEvent event) {
        logger.info("Connecté en tant que {}", event.getJDA().getSelfUser().getAsTag());
    }
}
