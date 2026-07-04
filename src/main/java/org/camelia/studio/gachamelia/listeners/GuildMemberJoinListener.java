package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.models.Element;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.models.WelcomeMessage;
import org.camelia.studio.gachamelia.services.RankService;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.EmbedUtils;

import java.awt.*;


public class GuildMemberJoinListener extends ListenerAdapter {
    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        Member member = event.getMember();
        User user = UserService.getInstance().getOrCreateUser(member.getId());

        WelcomeMessage welcomeMessage = RankService.getInstance().getRandomWelcomeMessage(user.getRank());

        TextChannel channel = event.getGuild().getTextChannelById(Configuration.getInstance().getDotenv().get("WELCOME_CHANNEL", "0"));

        Role role = event.getGuild().getRoleById(user.getRank().getDiscordId());
        Color color = new Color(0, 0, 0);

        if (role != null) {
            event.getGuild().addRoleToMember(member, role).queue();
            Color roleColor = role.getColors().getPrimary();
            if (roleColor != null) {
                color = roleColor;
            }
        }


        StringBuilder description = new StringBuilder();
        description.append("Bravo ! Vous venez d'invoquer ")
                .append(member.getAsMention()).append(" !\n")
                .append("Il s'agit d'un personnage de rareté ")
                .append(user.getRank().getName()).append(" ! ")
                .append(welcomeMessage.getMessage());

        description.append("\n\n")
                .append("__Caractéristiques principales__ :\n")
                .append("• Rôle « ").append(user.getRole().getName()).append(" ».").append("\n")
                .append("• Élément « ").append(user.getElements().stream().map(Element::getName).reduce("", (a, b) -> a + ", " + b).substring(2)).append(" ».").append("\n")
        ;

        EmbedBuilder embedBuilder = EmbedUtils.createDefaultEmbed(event.getJDA())
                .setTitle(event.getMember().getEffectiveName() + " vient d'être invoqué !")
                .setDescription(description)
                .setThumbnail(member.getUser().getEffectiveAvatarUrl())
                .setTimestamp(event.getMember().getTimeJoined())
                .setColor(color);


        if (channel != null) {
            channel.sendMessageEmbeds(embedBuilder.build()).queue();
        }

    }
}
