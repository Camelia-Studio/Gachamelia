package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.BotEmojiScheduler;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ReadyListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ReadyListener.class);

    private final BotApiService botApiService;
    private final GuildCatalogueCache catalogueCache;
    private final BotEmojiScheduler botEmojiScheduler;

    public ReadyListener(BotApiService botApiService, GuildCatalogueCache catalogueCache, BotEmojiScheduler botEmojiScheduler) {
        this.botApiService = botApiService;
        this.catalogueCache = catalogueCache;
        this.botEmojiScheduler = botEmojiScheduler;
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        logger.info("Connecté en tant que {}", event.getJDA().getSelfUser().getAsTag());
        botEmojiScheduler.start(event.getJDA());

        for (Guild guild : event.getJDA().getGuilds()) {
            CatalogueEnvelope catalogue = botApiService.initializeGuild(guild);
            guild.loadMembers().onSuccess(members -> initializeMembers(guild, catalogue, members));
        }
    }

    private void initializeMembers(Guild guild, CatalogueEnvelope catalogue, List<Member> members) {
        catalogueCache.put(guild.getId(), catalogue);
        String staffRoleId = catalogue.server().settings() != null ? catalogue.server().settings().staffRoleId() : null;
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
        String rankRoleId = envelope.user().rank().discordId();
        Role rankRole = rankRoleId != null ? guild.getRoleById(rankRoleId) : null;
        if (rankRole != null) {
            guild.addRoleToMember(member, rankRole).queue();
        }
    }
}
