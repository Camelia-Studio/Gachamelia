package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildJoinedEvent;
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildLifecycleListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildLifecycleListener.class);

    private final GuildRuntimeCoordinator coordinator;

    public GuildLifecycleListener(GuildRuntimeCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void onGuildJoin(@Nonnull GuildJoinEvent event) {
        coordinator.startGuild(event.getGuild());
    }

    @Override
    public void onUnavailableGuildJoined(@Nonnull UnavailableGuildJoinedEvent event) {
        coordinator.expectUnavailableGuild(event.getGuildId());
    }

    @Override
    public void onGuildAvailable(@Nonnull GuildAvailableEvent event) {
        coordinator.guildAvailable(event.getGuild());
    }

    @Override
    public void onGuildUnavailable(@Nonnull GuildUnavailableEvent event) {
        logger.info("Serveur {} indisponible, aucune desactivation demandee", event.getGuild().getId());
    }

    @Override
    public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
        coordinator.leaveGuild(event.getGuild().getId());
    }

    @Override
    public void onUnavailableGuildLeave(@Nonnull UnavailableGuildLeaveEvent event) {
        coordinator.leaveGuild(event.getGuildId());
    }
}
