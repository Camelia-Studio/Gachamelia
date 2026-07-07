package org.camelia.studio.gachamelia.managers;


import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.camelia.studio.gachamelia.api.BotApiService;
import org.camelia.studio.gachamelia.commands.personnage.FichePersoCommand;
import org.camelia.studio.gachamelia.commands.utils.PingCommand;
import org.camelia.studio.gachamelia.interfaces.ISlashCommand;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CommandManager {
    private final List<ISlashCommand> slashCommands;
    private final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    public CommandManager(BotApiService botApiService, GuildCatalogueCache catalogueCache) {
        slashCommands = List.of(
                new FichePersoCommand(botApiService, catalogueCache),
                new PingCommand()
        );
    }

    public void registerCommands(JDA jda) {
        jda
                .updateCommands()
                .addCommands(
                        slashCommands
                                .stream()
                                .map(
                                        (cmd) -> Commands.slash(cmd.getName(), cmd.getDescription()).addOptions(cmd.getOptions())
                                )
                                .toList()
                )
                .queue();


        logger.info("Enregistrement global de {} commandes", slashCommands.size());
    }

    public void handleCommand(String commandName, SlashCommandInteractionEvent event) {
        for (ISlashCommand command : slashCommands) {
            if (command.getName().equals(commandName)) {
                command.execute(event);
                return;
            }
        }
        event.getHook().editOriginal("Commande inconnue").queue();
    }
}
