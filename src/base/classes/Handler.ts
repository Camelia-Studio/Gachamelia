import {IHandler} from "../interfaces/IHandler";
import path from "node:path";
import {glob} from "glob";
import {CustomClient} from "./CustomClient";
import {Event} from "./Event";
import {Command} from "./Command";
import {SubCommand} from "./SubCommand";

export class Handler implements IHandler {
    client: CustomClient;
    constructor(client: CustomClient) {
        this.client = client;
    }
    async loadEvents(): Promise<void> {
        const files = (await glob(`dist/events/**/*.js`)).map(filePath => path.resolve(filePath));

        files.map(async (file: string) => {
            let evt = await import(file);
            let EventClass: any;
            for (const key in evt) {
                if (typeof evt[key] === 'function') {
                    EventClass = evt[key];
                    break;
                }
            }

            if (!EventClass) {
                return delete require.cache[require.resolve(file)] && console.log(`Event ${file.split('/').pop()} n'a pas de classe`);
            }

            const event : Event = new EventClass(this.client);

            if (!event.name)
                return delete require.cache[require.resolve(file)] && console.log(`Event ${file.split('/').pop()} n'a pas de nom`);

            const execute = (...args: any[]) => event.execute(...args);
            if (event.once) {
                this.client.once(event.name, execute);
            } else {
                this.client.on(event.name, execute);
            }

            console.log(`Event ${file.split('/').pop()} chargé`);

            return delete require.cache[require.resolve(file)];
        });
    }


    async loadCommands(): Promise<void> {
        const files = (await glob(`dist/commands/**/*.js`)).map(filePath => path.resolve(filePath));

        files.map(async (file: string) => {
            let evt = await import(file);
            let EventClass: any;
            for (const key in evt) {
                if (typeof evt[key] === 'function') {
                    EventClass = evt[key];
                    break;
                }
            }

            if (!EventClass) {
                return delete require.cache[require.resolve(file)] && console.log(`Event ${file.split('/').pop()} n'a pas de classe`);
            }

            const command : Command|SubCommand = new EventClass(this.client);

            if (!command.name)
                return delete require.cache[require.resolve(file)] && console.log(`Event ${file.split('/').pop()} n'a pas de nom`);

            if (file.split('/').pop()?.split(".")[2]) {
                console.log(`Sous Commande ${file.split('/').pop()} chargé`);
                this.client.subCommands.set(command.name, command as SubCommand);
            } else {
                console.log(`Commande ${file.split('/').pop()} chargé`);
                this.client.commands.set(command.name, command as Command);
            }

            return delete require.cache[require.resolve(file)];
        });
    }

}