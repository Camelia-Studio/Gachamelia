package org.camelia.studio.gachamelia.commands.personnage;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.api.dto.ApiRole;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.ApiUserStat;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.interfaces.ISlashCommand;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.EmbedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.Optional;

public class FichePersoCommand implements ISlashCommand {
    private static final Logger logger = LoggerFactory.getLogger(FichePersoCommand.class);

    private final BotApiService botApiService;
    private final GuildCatalogueCache catalogueCache;

    public FichePersoCommand(BotApiService botApiService, GuildCatalogueCache catalogueCache) {
        this.botApiService = botApiService;
        this.catalogueCache = catalogueCache;
    }

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

        try {
            EmbedBuilder embedGeneralite = EmbedUtils.createDefaultEmbed(event.getJDA());
            UserEnvelope envelope = botApiService.ensureUser(event.getGuild().getId(), member.getId());
            if (envelope.user() == null || envelope.user().rank() == null || envelope.user().role() == null) {
                event.getHook().editOriginal("La fiche de personnage n'a pas pu être chargée").queue();
                return;
            }

            ApiUser user = envelope.user();
            CatalogueEnvelope catalogue = catalogueCache.require(event.getGuild().getId());
            Role role = event.getGuild().getRoleById(user.rank().discordId());
            Color color = role != null && role.getColors().getPrimary() != null ? role.getColors().getPrimary() : Color.WHITE;
            Optional<ApiRole> catalogueRole = catalogue.catalogue().roles().stream()
                    .filter(candidate -> candidate.id() == user.role().id())
                    .findFirst();
            String emblem = catalogueRole.map(ApiRole::emoji).map(emoji -> emoji.markup() != null ? emoji.markup() : "Ø").orElse("Ø");

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
                    user.rank().name(),
                    user.role().name(),
                    user.elements().stream().map(ApiUser.ApiElementSummary::name).reduce((a, b) -> a + ", " + b).orElse("Ø"),
                    Configuration.getInstance().getDotenv().get("XP_EMOJI", "XP"),
                    0,
                    emblem
            ));

            for (ApiUserStat stat : user.stats()) {
                description.append("- %s : **%d** (%d + %d)\n".formatted(stat.name(), stat.value(), stat.value(), 0));
            }

            embedGeneralite.setTitle("Fiche de personnage");
            embedGeneralite.setColor(color);
            embedGeneralite.setThumbnail(member.getUser().getEffectiveAvatarUrl());
            embedGeneralite.setDescription(description.toString());

            event.getChannel().sendMessageEmbeds(List.of(
                    embedGeneralite.build()
            )).queue();

            event.getHook().editOriginal("Fiche de personnage de %s".formatted(member.getEffectiveName())).queue();
        } catch (RuntimeException exception) {
            logger.error(
                    "Impossible de charger la fiche perso pour le serveur {} et le membre {}",
                    event.getGuild().getId(),
                    member.getId(),
                    exception
            );
            event.getHook().editOriginal("La fiche de personnage n'a pas pu être chargée").queue();
        }
    }
}
