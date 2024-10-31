import {Event} from '../../base/classes/Event';
import {CustomClient} from "../../base/classes/CustomClient";
import {Collection, Events, REST, Routes} from "discord.js";
import {Command} from "../../base/classes/Command";
import {createAllUsers} from "../../base/utils/GachaUtils";
export class Ready extends Event {
    constructor(client: CustomClient) {
        super(client, {
            name: Events.ClientReady,
            once: true,
            description: 'Event se déclenchant lorsque le bot est prêt'
        });
    }

    async execute(...args: any[]): Promise<void> {
        console.log(`Connecté en tant que ${this.client.user?.tag}!`);

        const commands: object[] = this.getJson(this.client.commands);

        const rest = new REST({version: '10'}).setToken(this.client.config.token);
        const guildId = this.client.config.guildId;

        const setCommands: any = await rest.put(
            Routes.applicationGuildCommands(this.client.user?.id!, guildId),
            {
                body: commands
            }
        );

        console.log(`${setCommands.length} Commandes mises à jours avec succès !`);

        await createAllUsers(this.client);
    }

    private getJson(commands: Collection<string, Command>): object[] {
        const data: object[] = [];

        commands.forEach((command: Command) => {
            data.push({
                name: command.name,
                description: command.description,
                options: command.options,
                default_member_permissions: command.default_member_permissions.toString(),
                dm_permission: command.dm_permission,
            })
        });

        return data;
    }
}