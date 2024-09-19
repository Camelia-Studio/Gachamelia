import {ChatInputCommandInteraction, Collection, Events} from "discord.js";
import {CustomClient} from "../../base/classes/CustomClient";
import {Event} from "../../base/classes/Event";
import {Command} from "../../base/classes/Command";

export class CommandHandler extends Event {
    constructor(client: CustomClient) {
        super(client, {
            name: Events.InteractionCreate,
            once: false,
            description: 'Event se déclenchant lorsqu\'une interaction est créée'
        });
    }

    async execute(interaction: ChatInputCommandInteraction): Promise<void> {
        if (!interaction.isChatInputCommand()) return;

        const command: Command = this.client.commands.get(interaction.commandName) as Command;

        if (!command) {
            await interaction.reply({content: 'Commande inconnue', ephemeral: true})
            this.client.commands.delete(interaction.commandName);
            return;
        }
        const {cooldowns} = this.client;

        if (!cooldowns.has(command.name)) {
            cooldowns.set(command.name, new Collection());
        }

        const now = Date.now();
        const timestamps = cooldowns.get(command.name)!;
        const cooldownAmount = (command.cooldown || 3) * 1000;

        if (timestamps.has(interaction.user.id) && now < (timestamps.get(interaction.user.id) || 0) + cooldownAmount) {
            const timeLeft = (((timestamps.get(interaction.user.id) || 0) + cooldownAmount - now) / 1000).toFixed(1);
            await interaction.reply({
                content: `Veuillez patienter ${timeLeft} secondes avant de réutiliser la commande \`${command.name}\``,
                ephemeral: true
            });

            return;
        }
        timestamps.set(interaction.user.id, now);
        setTimeout(() => timestamps.delete(interaction.user.id), cooldownAmount);

        try {
            const subCommandGroup = interaction.options.getSubcommandGroup(false);
            const subCommand = `${interaction.commandName}${subCommandGroup ? `.${subCommandGroup}` : ''}.${interaction.options.getSubcommand(false)}` || '';

            this.client.subCommands.get(subCommand)?.execute(interaction) || command.execute(interaction);
        } catch (error) {
            console.error(error);
        }
    }
}