import {Command} from "../base/classes/Command";
import {CustomClient} from "../base/classes/CustomClient";
import {Category} from "../base/enums/Category";
import {PermissionsBitField} from "discord.js";

export class PingCommand extends Command {
    constructor(client: CustomClient) {
        super(client, {
            name: 'ping',
            description: ' Pong!',
            category: Category.UTILITIES,
            options: [],
            default_member_permissions: PermissionsBitField.Flags.UseApplicationCommands,
            dm_permission: true,
            cooldown: 5
        });
    }

    execute(interaction: any): void {
        interaction.reply({
            content: `Pong!`,
            ephemeral: true
        });
    }
}