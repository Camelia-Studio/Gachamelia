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
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.utils.EmbedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class GuildMemberJoinListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildMemberJoinListener.class);
    private final BotApiService botApiService;
    private final GuildCatalogueCache catalogueCache;
    private final CatalogueMessageService messageService;

    public GuildMemberJoinListener(
            BotApiService botApiService,
            GuildCatalogueCache catalogueCache,
            CatalogueMessageService messageService
    ) {
        this.botApiService = botApiService;
        this.catalogueCache = catalogueCache;
        this.messageService = messageService;
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        Member member = event.getMember();
        try {
            UserEnvelope envelope = botApiService.ensureUser(event.getGuild().getId(), member.getId());
            if (envelope.user() == null || envelope.user().rank() == null || envelope.user().role() == null) {
                return;
            }

            CatalogueEnvelope catalogue = catalogueCache.require(event.getGuild().getId());
            String welcomeChannelId = catalogue.server().settings() != null ? catalogue.server().settings().welcomeChannelId() : null;
            if (welcomeChannelId == null || welcomeChannelId.isBlank()) {
                return;
            }

            TextChannel channel = event.getGuild().getTextChannelById(welcomeChannelId);
            if (channel == null) {
                return;
            }

            Role role = event.getGuild().getRoleById(envelope.user().rank().discordId());
            Color color = new Color(0, 0, 0);

            if (role != null) {
                event.getGuild().addRoleToMember(member, role).queue();
                Color roleColor = role.getColors().getPrimary();
                if (roleColor != null) {
                    color = roleColor;
                }
            }

            StringBuilder description = new StringBuilder();
            ApiUser user = envelope.user();
            String welcomeMessage = messageService.randomWelcomeMessage(catalogue, user.rank().id()).orElse("");
            description.append("Bravo ! Vous venez d'invoquer ")
                    .append(member.getAsMention()).append(" !\n")
                    .append("Il s'agit d'un personnage de rareté ")
                    .append(user.rank().name()).append(" ! ");

            if (!welcomeMessage.isBlank()) {
                description.append(welcomeMessage);
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
