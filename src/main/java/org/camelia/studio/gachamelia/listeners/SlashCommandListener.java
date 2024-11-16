package org.camelia.studio.gachamelia.listeners;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.camelia.studio.gachamelia.managers.CommandManager;
import org.jetbrains.annotations.NotNull;

public class SlashCommandListener extends ListenerAdapter {
    private final CommandManager commandManager;

    public SlashCommandListener() {
        commandManager = new CommandManager();
        commandManager.registerCommands();
    }


    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();
        commandManager.handleCommand(event.getName(), event);
    }
}
