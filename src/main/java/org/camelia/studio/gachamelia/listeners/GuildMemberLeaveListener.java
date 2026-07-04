package org.camelia.studio.gachamelia.listeners;

import jakarta.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.models.ByeMessage;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.services.RankService;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.EmbedUtils;

import java.awt.*;


public class GuildMemberLeaveListener extends ListenerAdapter {

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {

        net.dv8tion.jda.api.entities.User discordUser = event.getUser();
        User user = UserService.getInstance().getOrCreateUser(discordUser.getId());

        ByeMessage byeMessage = RankService.getInstance().getRandomByeMessage(user.getRank());

        TextChannel channel = event.getGuild().getTextChannelById(Configuration.getInstance().getDotenv().get("WELCOME_CHANNEL", "0"));


        Role role = event.getGuild().getRoleById(user.getRank().getDiscordId());
        Color color = new Color(0, 0, 0);

        if (role != null) {
            Color roleColor = role.getColors().getPrimary();
            if (roleColor != null) {
                color = roleColor;
            }
        }


        StringBuilder description = new StringBuilder();
        description.append(byeMessage.getMessage().replace("%username%", "**" + discordUser.getEffectiveName() + "**"));

        EmbedBuilder embedBuilder = EmbedUtils.createDefaultEmbed(event.getJDA())
                .setTitle(user.getRank().getByeTitle() != null ? user.getRank().getByeTitle() : "Au revoir, %s !".formatted(discordUser.getEffectiveName()))
                .setDescription(description)
                .setThumbnail(discordUser.getEffectiveAvatarUrl())
                .setColor(color);


        if (channel != null) {
            channel.sendMessageEmbeds(embedBuilder.build()).queue();
        }
    }
}
