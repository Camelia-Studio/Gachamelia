package org.camelia.studio.gachamelia.commands.personnage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.camelia.studio.gachamelia.interfaces.ISlashCommand;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.services.UserService;

import java.awt.*;
import java.util.List;

public class FichePersoCommand implements ISlashCommand {
    @Override
    public String getName() {
        return "ficheperso";
    }

    @Override
    public String getDescription() {
        return "Permet d'afficher la fiche de personnage de l'utilisateur";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.USER, "utilisateur", "L'utilisateur dont vous souhaitez afficher la fiche de personnage").setRequired(false)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping utilisateur = event.getOption("utilisateur");
        Member member = utilisateur != null ? utilisateur.getAsMember() : event.getMember();

        if (member == null) {
            event.getHook().editOriginal("L'utilisateur n'a pas été trouvé").queue();
            return;
        }

        if (event.getGuild() == null) {
            event.getHook().editOriginal("Le serveur n'a pas été trouvé").queue();
            return;
        }

        EmbedBuilder embedGeneralite = new EmbedBuilder();
        User user = UserService.getInstance().getOrCreateUser(member.getId());
        Role role = event.getGuild().getRoleById(user.getRank().getDiscordId());
        Color color = role != null ? role.getColor() : Color.WHITE;

        embedGeneralite.setAuthor(member.getEffectiveName(), null, user.getRole().getImageUrl());
        embedGeneralite.setTitle("Fiche de personnage");
        embedGeneralite.setColor(color);
        embedGeneralite.setThumbnail(member.getUser().getEffectiveAvatarUrl());
        embedGeneralite.setDescription("""
                __Caractéristiques principales__ :
                - Nom : **%s**
                - Rareté : **%s**
                - Rôle : **%s**
                - Éléments : *%s*
                - Xp : **%d**
                - Emblème : **%s**
                __Statistiques de combat__ :
                - Éther : **%d** (%d + 0)
                - Astral : **%d** (%d + 0)
                - Impact : **%d** (%d + 0)
                - Aura : **%d** (%d + 0)
                - Égide : **%d** (%d + 0)
                - Oracle : **%d** (%d + 0)
                """.formatted(
                member.getEffectiveName(),
                user.getRank().getName(),
                user.getRole().getName(),
                String.join(", ", user.getElement().getName()),
                0,
                "Ø",
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0,
                0, 0
        ));

        event.getChannel().sendMessageEmbeds(List.of(
                embedGeneralite.build()
        )).queue();

        event.getHook().editOriginal("Fiche de personnage de %s".formatted(member.getEffectiveName())).queue();
    }
}
