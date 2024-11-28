package org.camelia.studio.gachamelia.commands.personnage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.camelia.studio.gachamelia.interfaces.ISlashCommand;
import org.camelia.studio.gachamelia.models.Element;
import org.camelia.studio.gachamelia.models.User;
import org.camelia.studio.gachamelia.models.UserStat;
import org.camelia.studio.gachamelia.repositories.StatRepository;
import org.camelia.studio.gachamelia.services.UserService;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.EmbedUtils;

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

        EmbedBuilder embedGeneralite = EmbedUtils.createDefaultEmbed(event.getJDA());
        User user = UserService.getInstance().getOrCreateUser(member.getId());
        Role role = event.getGuild().getRoleById(user.getRank().getDiscordId());
        Color color = role != null ? role.getColor() : Color.WHITE;
        List<UserStat> stats = StatRepository.getInstance().getUserStats(user);

        StringBuilder description = new StringBuilder("""
                __Caractéristiques principales__ :
                - Nom : **%s**
                - Rareté : **%s**
                - Rôle : **%s**
                - Éléments : *%s*
                - %s : **%d**
                - Emblème : **%s**
                __Statistiques de combat__ :
                """.formatted(
                member.getEffectiveName(),
                user.getRank().getName(),
                user.getRole().getName(),
                user.getElements().stream().map(Element::getName).reduce("", (a, b) -> a + ", " + b).substring(2),
                Configuration.getInstance().getDotenv().get("XP_EMOJI", "XP"),
                0,
                "Ø"
        ));

        for (UserStat stat : stats) {
            int userStat = stat.getValue();
            int equipmentStat = 0;
            description.append("- %s : **%d** (%d + %d)\n".formatted(stat.getStat().getName(), userStat + equipmentStat, userStat, equipmentStat));
        }

        embedGeneralite.setAuthor(member.getEffectiveName(), null, user.getRole().getImageUrl());
        embedGeneralite.setTitle("Fiche de personnage");
        embedGeneralite.setColor(color);
        embedGeneralite.setThumbnail(member.getUser().getEffectiveAvatarUrl());
        embedGeneralite.setDescription(description.toString());

        event.getChannel().sendMessageEmbeds(List.of(
                embedGeneralite.build()
        )).queue();

        event.getHook().editOriginal("Fiche de personnage de %s".formatted(member.getEffectiveName())).queue();
    }
}
