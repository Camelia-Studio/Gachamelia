package org.camelia.studio.gachamelia.commands.utils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.camelia.studio.gachamelia.interfaces.ISlashCommand;

public class PingCommand implements ISlashCommand {
    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Envoie pong !";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        event.getHook().editOriginal("Pong !").queue();
    }
}
