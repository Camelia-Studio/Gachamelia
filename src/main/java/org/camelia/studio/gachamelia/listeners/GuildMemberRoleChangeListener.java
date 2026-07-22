package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.GuildNotReadyException;
import org.camelia.studio.gachamelia.services.GuildRuntimeCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildMemberRoleChangeListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuildMemberRoleChangeListener.class);
    private final BotApiService botApiService;
    private final GuildRuntimeCoordinator coordinator;
    private final boolean syncBotMembers;

    public GuildMemberRoleChangeListener(BotApiService botApiService, GuildRuntimeCoordinator coordinator) {
        this(botApiService, coordinator, false);
    }

    public GuildMemberRoleChangeListener(
            BotApiService botApiService,
            GuildRuntimeCoordinator coordinator,
            boolean syncBotMembers
    ) {
        this.botApiService = botApiService;
        this.coordinator = coordinator;
        this.syncBotMembers = syncBotMembers;
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        Member member = event.getMember();
        if (!syncBotMembers && member.getUser().isBot()) {
            return;
        }
        if (coordinator.findReadyCatalogue(event.getGuild().getId()).isEmpty()) {
            return;
        }
        try {
            CatalogueEnvelope catalogue = coordinator.findReadyCatalogue(event.getGuild().getId())
                    .orElseThrow(() -> new GuildNotReadyException(event.getGuild().getId()));
            String staffRoleId = catalogue.server().settings() != null ? catalogue.server().settings().staffRoleId() : null;

            if (staffRoleId == null || event.getRoles().stream().noneMatch(role -> role.getId().equals(staffRoleId))) {
                return;
            }

            UserEnvelope envelope = coordinator.executeRuntime(
                    event.getGuild(),
                    () -> botApiService.ensureStaffUser(event.getGuild().getId(), member.getId())
            );
            if (envelope.user() == null || envelope.user().rank() == null) {
                return;
            }

            coordinator.findReadyCatalogue(event.getGuild().getId())
                    .orElseThrow(() -> new GuildNotReadyException(event.getGuild().getId()));

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
