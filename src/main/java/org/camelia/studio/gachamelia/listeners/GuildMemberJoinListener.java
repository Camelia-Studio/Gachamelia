package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.CatalogueMessageService;
import org.camelia.studio.gachamelia.services.GuildNotReadyException;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.camelia.studio.gachamelia.utils.EmbedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

public class GuildMemberJoinListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildMemberJoinListener.class);
    private final BotApiService botApiService;
    private final GuildRuntimeCoordinator coordinator;
    private final CatalogueMessageService messageService;
    private final boolean syncBotMembers;

    public GuildMemberJoinListener(
            BotApiService botApiService,
            GuildRuntimeCoordinator coordinator,
            CatalogueMessageService messageService
    ) {
        this(botApiService, coordinator, messageService, false);
    }

    public GuildMemberJoinListener(
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
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        Member member = event.getMember();
        if (!syncBotMembers && member.getUser().isBot()) {
            return;
        }
        if (coordinator.findReadyCatalogue(event.getGuild().getId()).isEmpty()) {
            return;
        }
        try {
            UserEnvelope envelope = coordinator.executeRuntime(
                    event.getGuild(),
                    () -> botApiService.ensureUser(event.getGuild().getId(), member.getId())
            );
            if (envelope.user() == null || envelope.user().rank() == null || envelope.user().role() == null) {
                return;
            }

            CatalogueEnvelope catalogue = coordinator.findReadyCatalogue(event.getGuild().getId())
                    .orElseThrow(() -> new GuildNotReadyException(event.getGuild().getId()));
            Role role = event.getGuild().getRoleById(envelope.user().rank().discordId());
            Color color = new Color(0, 0, 0);

            if (role != null) {
                event.getGuild().addRoleToMember(member, role).queue();
                Color roleColor = role.getColors().getPrimary();
                if (roleColor != null) {
                    color = roleColor;
                }
            }

            String welcomeChannelId = catalogue.server().settings() != null ? catalogue.server().settings().welcomeChannelId() : null;
            if (welcomeChannelId == null || welcomeChannelId.isBlank()) {
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(welcomeChannelId);
            if (channel == null) {
                return;
            }

            StringBuilder description = new StringBuilder();
            ApiUser user = envelope.user();
            String welcomeMessage = messageService.randomWelcomeMessage(catalogue, user.rank().id()).orElse("");
            description.append("Bravo ! Vous venez d'invoquer ")
                    .append(member.getAsMention()).append(" !\n")
                    .append("Il s'agit d'un personnage de rareté ")
                    .append(user.rank().name()).append(" ! ");

            if (!welcomeMessage.isBlank()) {
                description.append(welcomeMessage.replace("%username%", "**" + member.getEffectiveName() + "**"));
            }

            description.append("\n\n")
                    .append("__Caractéristiques principales__ :\n")
                    .append("• Rôle « ").append(user.role().name()).append(" ».").append("\n")
                    .append("• Élément « ")
                    .append(user.elements().stream().map(ApiUser.ApiElementSummary::name).reduce((a, b) -> a + ", " + b).orElse("Ø"))
                    .append(" ».").append("\n");

            EmbedBuilder embedBuilder = EmbedUtils.createDefaultEmbed(event.getJDA())
                    .setTitle(event.getMember().getEffectiveName() + " vient d'être invoqué !")
                    .setDescription(description)
                    .setThumbnail(member.getUser().getEffectiveAvatarUrl())
                    .setTimestamp(event.getMember().getTimeJoined())
                    .setColor(color);
            channel.sendMessageEmbeds(embedBuilder.build()).queue();
        } catch (RuntimeException exception) {
            logger.warn(
                    "Impossible de traiter l'arrivee du membre {} sur le serveur {} ({})",
                    member.getId(),
                    event.getGuild().getId(),
                    event.getGuild().getName(),
                    exception
            );
        }
    }
}
