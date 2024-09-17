import Event from '../../base/classes/Event';
import CustomClient from "../../base/classes/CustomClient";
import {Events} from "discord.js";
export default class Ready extends Event {
    constructor(client: CustomClient) {
        super(client, {
            name: Events.ClientReady,
            once: true,
            description: 'Event se déclenchant lorsque le bot est prêt'
        });
    }

    execute(...args: any[]): void {
        console.log(`Connecté en tant que ${this.client.user?.tag}!`);
    }
}