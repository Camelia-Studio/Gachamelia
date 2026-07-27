package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.JDA;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.CatalogueRefreshScheduler;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadyListener {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);

    private final GuildRuntimeCoordinator coordinator;
    private final BotEmojiScheduler botEmojiScheduler;
    private final CatalogueRefreshScheduler catalogueRefreshScheduler;

    public ReadyListener(
            GuildRuntimeCoordinator coordinator,
            BotEmojiScheduler botEmojiScheduler,
            CatalogueRefreshScheduler catalogueRefreshScheduler
    ) {
        this.coordinator = coordinator;
        this.botEmojiScheduler = botEmojiScheduler;
        this.catalogueRefreshScheduler = catalogueRefreshScheduler;
    }

    public void initialize(JDA jda) {
        logger.info("Connecté en tant que {}", jda.getSelfUser().getAsTag());
        botEmojiScheduler.start(jda);
        jda.getGuilds().forEach(coordinator::startGuild);
        catalogueRefreshScheduler.start(jda);
    }
}
