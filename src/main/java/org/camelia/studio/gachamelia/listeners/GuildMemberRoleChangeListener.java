package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildMemberRoleChangeListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildMemberRoleChangeListener.class);
    private final BotApiService botApiService;
    private final GuildCatalogueCache catalogueCache;

    public GuildMemberRoleChangeListener(BotApiService botApiService, GuildCatalogueCache catalogueCache) {
        this.botApiService = botApiService;
        this.catalogueCache = catalogueCache;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        Member member = event.getMember();
        try {
            CatalogueEnvelope catalogue = catalogueCache.require(event.getGuild().getId());
            String staffRoleId = catalogue.server().settings() != null ? catalogue.server().settings().staffRoleId() : null;

            if (staffRoleId == null || event.getRoles().stream().noneMatch(role -> role.getId().equals(staffRoleId))) {
                return;
            }

            UserEnvelope envelope = botApiService.ensureStaffUser(event.getGuild().getId(), member.getId());
            if (envelope.user() == null || envelope.user().rank() == null) {
                return;
            }

            Role discordRole = event.getGuild().getRoleById(envelope.user().rank().discordId());
            if (discordRole != null) {
                event.getGuild().addRoleToMember(member, discordRole).queue();
            }
        } catch (RuntimeException exception) {
            logger.warn(
                    "Impossible de traiter l'ajout de roles pour le membre {} sur le serveur {} ({})",
                    member.getId(),
                    event.getGuild().getId(),
                    event.getGuild().getName(),
                    exception
            );
        }
    }
}
