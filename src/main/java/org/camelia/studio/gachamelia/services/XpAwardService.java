package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.ApiRank;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.api.dto.XpAwardEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class XpAwardService {
    private static final Logger logger = LoggerFactory.getLogger(XpAwardService.class);
    private final BotApiService botApiService;
    private final GuildRuntimeCoordinator coordinator;

    public XpAwardService(BotApiService botApiService, GuildRuntimeCoordinator coordinator) {
        this.botApiService = botApiService;
        this.coordinator = coordinator;
    }

    public boolean configured(String guildId) {
        return coordinator.findReadyCatalogue(guildId)
                .map(envelope -> envelope.catalogue().progression())
                .filter(settings -> settings != null && settings.configured())
                .isPresent();
    }

    public int voiceIntervalMinutes(String guildId) {
        return coordinator.findReadyCatalogue(guildId)
                .map(envelope -> envelope.catalogue().progression())
                .filter(settings -> settings != null && settings.configured())
                .map(settings -> settings.voiceIntervalMinutes())
                .orElse(0);
    }

    public boolean award(Guild guild, Member member, String source) {
        if (member.getUser().isBot() || !configured(guild.getId())) {
            return false;
        }
        try {
            return coordinator.executeRuntime(guild, () -> {
                UserEnvelope ensured = botApiService.ensureUser(guild.getId(), member.getId());
                ApiUser before = ensured.user();
                if (before == null || before.rank() == null || before.rank().staff()) {
                    return false;
                }
                XpAwardEnvelope awarded = botApiService.awardXp(guild.getId(), member.getId(), source);
                ApiUser after = awarded.user();
                if (after == null || after.rank() == null) {
                    return false;
                }
                synchronizeRankRole(guild, member, after.rank().discordId());
                return true;
            });
        } catch (RuntimeException exception) {
            logger.warn("Impossible d'attribuer l'XP {} au membre {} du serveur {}", source, member.getId(), guild.getId(), exception);
            return false;
        }
    }

    private void synchronizeRankRole(Guild guild, Member member, String desiredDiscordRoleId) {
        Role desired = guild.getRoleById(desiredDiscordRoleId);
        if (desired == null) {
            return;
        }
        var envelope = coordinator.findReadyCatalogue(guild.getId()).orElse(null);
        if (envelope == null || envelope.catalogue().progression() == null) {
            return;
        }
        var progressionIds = envelope.catalogue().progression().rankIds();
        List<Role> removals = envelope.catalogue().ranks().stream()
                .filter(rank -> progressionIds.contains(rank.id()) && !rank.discordId().equals(desiredDiscordRoleId))
                .map(ApiRank::discordId)
                .map(guild::getRoleById)
                .filter(role -> role != null && member.getRoles().contains(role))
                .toList();
        if (!member.getRoles().contains(desired) || !removals.isEmpty()) {
            guild.modifyMemberRoles(member, List.of(desired), removals).queue(
                    ignored -> {},
                    exception -> logger.warn("Impossible de synchroniser le rang Discord du membre {} sur {}", member.getId(), guild.getId(), exception)
            );
        }
    }
}
