package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.models.WelcomeMessage;
import org.camelia.studio.gachamelia.services.RankService;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.MessageUtils;

import java.awt.*;
import java.util.Map;


public class GuildMemberJoinListener extends ListenerAdapter {
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        User user = UserService.getInstance().getOrCreateUser(member.getId());

        WelcomeMessage welcomeMessage = RankService.getInstance().getRandomWelcomeMessage(user.getRank());

        TextChannel channel = event.getGuild().getTextChannelById(Configuration.getInstance().getDotenv().get("WELCOME_CHANNEL", "0"));
        String description = MessageUtils.insertPlaceholders(welcomeMessage.getMessage(), Map.of(
                "username", member.getEffectiveName(),
                "rank", user.getRank().getName()
        ));

        Role role = event.getGuild().getRoleById(user.getRank().getDiscordId());
        Color color = new Color(0, 0, 0);

        if (role != null) {
            event.getGuild().addRoleToMember(member, role).queue();
            color = role.getColor();
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Bienvenue sur le serveur " + event.getGuild().getName() + " !")
                .setDescription(description)
                .setThumbnail(member.getUser().getAvatarUrl())
                .setTimestamp(event.getMember().getTimeJoined())
                .setColor(color)
                ;


        if (channel != null) {
            channel.sendMessageEmbeds(embedBuilder.build()).queue();
        }

    }
}
