package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildLifecycleListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildLifecycleListener.class);

    private final ReadyListener readyListener;
    private final GuildCatalogueCache catalogueCache;

    public GuildLifecycleListener(ReadyListener readyListener, GuildCatalogueCache catalogueCache) {
        this.readyListener = readyListener;
        this.catalogueCache = catalogueCache;
    }

    @Override
    public void onGuildJoin(@Nonnull GuildJoinEvent event) {
        Guild guild = event.getGuild();
        try {
            readyListener.initializeGuild(guild);
            logger.info("Serveur {} ({}) initialise apres invitation du bot", guild.getId(), guild.getName());
        } catch (RuntimeException exception) {
            logger.warn(
                    "Impossible d'initialiser le serveur {} ({}) apres invitation du bot",
                    guild.getId(),
                    guild.getName(),
                    exception
            );
        }
    }

    @Override
    public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
        Guild guild = event.getGuild();
        removeCatalogue(guild.getId(), guild.getName());
    }

    @Override
    public void onUnavailableGuildLeave(@Nonnull UnavailableGuildLeaveEvent event) {
        removeCatalogue(event.getGuildId(), "indisponible");
    }

    private void removeCatalogue(String guildId, String guildName) {
        try {
            catalogueCache.remove(guildId);
            logger.info("Catalogue local du serveur {} ({}) oublie apres depart du bot", guildId, guildName);
        } catch (RuntimeException exception) {
            logger.warn(
                    "Impossible d'oublier le catalogue local du serveur {} ({}) apres depart du bot",
                    guildId,
                    guildName,
                    exception
            );
        }
    }
}
