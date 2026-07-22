package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiRank;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.CatalogueMessageService;
import org.camelia.studio.gachamelia.services.GuildNotReadyException;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.camelia.studio.gachamelia.utils.EmbedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

public class GuildMemberLeaveListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildMemberLeaveListener.class);
    private final BotApiService botApiService;
    private final GuildRuntimeCoordinator coordinator;
    private final CatalogueMessageService messageService;
    private final boolean syncBotMembers;

    public GuildMemberLeaveListener(
            BotApiService botApiService,
            GuildRuntimeCoordinator coordinator,
            CatalogueMessageService messageService
    ) {
        this(botApiService, coordinator, messageService, false);
    }

    public GuildMemberLeaveListener(
            BotApiService botApiService,
            GuildRuntimeCoordinator coordinator,
            CatalogueMessageService messageService,
            boolean syncBotMembers
    ) {
        this.botApiService = botApiService;
        this.coordinator = coordinator;
        this.messageService = messageService;
        this.syncBotMembers = syncBotMembers;
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        net.dv8tion.jda.api.entities.User discordUser = event.getUser();
        if (!syncBotMembers && discordUser.isBot()) {
            return;
        }
        if (coordinator.findReadyCatalogue(event.getGuild().getId()).isEmpty()) {
            return;
        }
        try {
            UserEnvelope envelope = coordinator.executeRuntime(
                    event.getGuild(),
                    () -> botApiService.ensureUser(event.getGuild().getId(), discordUser.getId())
            );
            if (envelope.user() == null || envelope.user().rank() == null) {
                return;
            }

            CatalogueEnvelope catalogue = coordinator.findReadyCatalogue(event.getGuild().getId())
                    .orElseThrow(() -> new GuildNotReadyException(event.getGuild().getId()));
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
            String fallbackTitle = "Au revoir, %s !".formatted(discordUser.getEffectiveName());
            String title = messageService.findRank(catalogue, envelope.user().rank().id())
                    .map(ApiRank::byeTitle)
                    .filter(value -> !value.isBlank())
                    .orElse(fallbackTitle);
            StringBuilder description = new StringBuilder();
            description.append(byeMessage.replace("%username%", "**" + discordUser.getEffectiveName() + "**"));

            EmbedBuilder embedBuilder = EmbedUtils.createDefaultEmbed(event.getJDA())
                    .setTitle(title)
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
