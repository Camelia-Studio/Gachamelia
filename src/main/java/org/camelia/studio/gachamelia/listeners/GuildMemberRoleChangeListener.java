package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.models.Rank;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.repositories.RankRepository;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;

public class GuildMemberRoleChangeListener extends ListenerAdapter {
    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        Member member = event.getMember();
        Role staffRole = event.getGuild().getRoleById(Configuration.getInstance().getDotenv().get("STAFF_ROLE"));
        Rank rankStaff = RankRepository.getInstance().getRankStaff();
        User user = UserService.getInstance().getOrCreateUser(member.getId());

        if (staffRole != null && member.getRoles().contains(staffRole) && (user.getRank() == null || !user.getRank().isStaff())) {
            user.setRank(rankStaff);
            UserService.getInstance().updateUser(user);

            Role discordRole = event.getGuild().getRoleById(rankStaff.getDiscordId());

            if (discordRole != null) {
                event.getGuild().addRoleToMember(member, discordRole).queue();
            }
        }
    }
}
