package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.CatalogueMessageService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.utils.EmbedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class GuildMemberLeaveListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildMemberLeaveListener.class);
    private final BotApiService botApiService;
    private final GuildCatalogueCache catalogueCache;
    private final CatalogueMessageService messageService;

    public GuildMemberLeaveListener(
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            CatalogueMessageService messageService
    ) {
        this.botApiService = botApiService;
        this.catalogueCache = catalogueCache;
        this.messageService = messageService;
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        net.dv8tion.jda.api.entities.User discordUser = event.getUser();
        try {
            UserEnvelope envelope = botApiService.ensureUser(event.getGuild().getId(), discordUser.getId());
            if (envelope.user() == null || envelope.user().rank() == null) {
                return;
            }

            CatalogueEnvelope catalogue = catalogueCache.require(event.getGuild().getId());
            String byeChannelId = catalogue.server().settings() != null ? catalogue.server().settings().byeChannelId() : null;
            if (byeChannelId == null || byeChannelId.isBlank()) {
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(byeChannelId);
            if (channel == null) {
                return;
            }

            Role role = event.getGuild().getRoleById(envelope.user().rank().discordId());
            Color color = new Color(0, 0, 0);

            if (role != null) {
                Color roleColor = role.getColors().getPrimary();
                if (roleColor != null) {
                    color = roleColor;
                }
            }

            String byeMessage = messageService.randomByeMessage(catalogue, envelope.user().rank().id()).orElse("");
            StringBuilder description = new StringBuilder();
            description.append(byeMessage.replace("%username%", "**" + discordUser.getEffectiveName() + "**"));

            EmbedBuilder embedBuilder = EmbedUtils.createDefaultEmbed(event.getJDA())
                    .setTitle("Au revoir, %s !".formatted(discordUser.getEffectiveName()))
                    .setDescription(description)
                    .setThumbnail(discordUser.getEffectiveAvatarUrl())
                    .setColor(color);
            channel.sendMessageEmbeds(embedBuilder.build()).queue();
        } catch (RuntimeException exception) {
            logger.warn(
                    "Impossible de traiter le depart de l'utilisateur {} sur le serveur {} ({})",
                    discordUser.getId(),
                    event.getGuild().getId(),
                    event.getGuild().getName(),
                    exception
            );
        }
    }
}
