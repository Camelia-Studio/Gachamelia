package org.camelia.studio.gachamelia.managers;


import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.camelia.studio.gachamelia.Gachamelia;
import org.camelia.studio.gachamelia.interfaces.ISlashCommand;
import org.camelia.studio.gachamelia.utils.Configuration;
import org.camelia.studio.gachamelia.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CommandManager {
    private final List<ISlashCommand> slashCommands;
    private final Logger logger = LoggerFactory.getLogger(CommandManager.class);

    public CommandManager() {
        slashCommands = ReflectionUtils.loadClasses(
                "org.camelia.studio.gachamelia.commands",
                ISlashCommand.class
        );
    }

    public void registerCommands() {
        Guild guild = Gachamelia
                .getJda()
                .getGuildById(Configuration.getInstance().getDotenv().get("GUILD_ID"));

        if (guild == null) {
            logger.error("Impossible de trouver le serveur Discord");
            return;
        }

        guild
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


        logger.info("Enregistrement de {} commandes", slashCommands.size());
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

