package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.camelia.studio.gachamelia.api.ApiException;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ReadyListener {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);

    private final BotApiService botApiService;
    private final BotEmojiScheduler botEmojiScheduler;

    public ReadyListener(BotApiService botApiService, BotEmojiScheduler botEmojiScheduler) {
        this.botApiService = botApiService;
        this.botEmojiScheduler = botEmojiScheduler;
    }

    public void initialize(JDA jda) {
        logger.info("Connecté en tant que {}", jda.getSelfUser().getAsTag());
        botEmojiScheduler.start(jda);

        for (Guild guild : jda.getGuilds()) {
            CatalogueEnvelope catalogue = botApiService.initializeGuild(guild);
            if (!canInitializeMembers(guild, catalogue)) {
                continue;
            }
            List<Member> members = guild.loadMembers().get();
            initializeMembers(guild, catalogue, members);
        }
    }

    private boolean canInitializeMembers(Guild guild, CatalogueEnvelope catalogue) {
        if (catalogue == null || catalogue.catalogue() == null) {
            logger.warn("Catalogue absent pour le serveur {}, initialisation des membres ignorée", guild.getId());
            return false;
        }
        if (catalogue.catalogue().ranks() == null || catalogue.catalogue().ranks().isEmpty()) {
            logger.warn("Catalogue de rangs vide pour le serveur {}, initialisation des membres ignorée", guild.getId());
            return false;
        }
        if (catalogue.catalogue().roles() == null || catalogue.catalogue().roles().isEmpty()) {
            logger.warn("Catalogue de rôles vide pour le serveur {}, initialisation des membres ignorée", guild.getId());
            return false;
        }
        if (catalogue.catalogue().elements() == null || catalogue.catalogue().elements().isEmpty()) {
            logger.warn("Catalogue d'éléments vide pour le serveur {}, initialisation des membres ignorée", guild.getId());
            return false;
        }
        return true;
    }

    private void initializeMembers(Guild guild, CatalogueEnvelope catalogue, List<Member> members) {
        ApiDiscordServer server = requireServer(guild, catalogue);
        String staffRoleId = server.settings() != null
                ? server.settings().staffRoleId()
                : null;
        Role staffRole = staffRoleId != null ? guild.getRoleById(staffRoleId) : null;

        for (Member member : members) {
            UserEnvelope envelope = staffRole != null && member.getRoles().contains(staffRole)
                    ? botApiService.ensureStaffUser(guild.getId(), member.getId())
                    : botApiService.ensureUser(guild.getId(), member.getId());
            addRankRole(guild, member, envelope);
            logger.info("Utilisateur {} initialisé", member.getUser().getAsTag());
        }
    }

    private void addRankRole(Guild guild, Member member, UserEnvelope envelope) {
        ApiUser user = requireUser(guild, member, envelope);
        if (user.rank() == null) {
            throw new ApiException(
                    502,
                    "api_user_rank_missing",
                    "Missing rank in API user payload for guild %s and member %s".formatted(guild.getId(), member.getId())
            );
        }
        String rankRoleId = user.rank().discordId();
        Role rankRole = rankRoleId != null ? guild.getRoleById(rankRoleId) : null;
        if (rankRole != null) {
            guild.addRoleToMember(member, rankRole).queue();
        }
    }

    private ApiDiscordServer requireServer(Guild guild, CatalogueEnvelope catalogue) {
        if (catalogue.server() == null) {
            throw new ApiException(
                    502,
                    "catalogue_server_missing",
                    "Missing server in catalogue payload for guild %s".formatted(guild.getId())
            );
        }
        return catalogue.server();
    }

    private ApiUser requireUser(Guild guild, Member member, UserEnvelope envelope) {
        if (envelope.user() == null) {
            throw new ApiException(
                    502,
                    "api_user_missing",
                    "Missing user in API payload for guild %s and member %s".formatted(guild.getId(), member.getId())
            );
        }
        return envelope.user();
    }
}
