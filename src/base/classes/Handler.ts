import IHandler from "../interfaces/IHandler";
import path from "node:path";
import {glob} from "glob";
import CustomClient from "./CustomClient";
import Event from "./Event";

export default class Handler implements IHandler {
    client: CustomClient;
    constructor(client: CustomClient) {
        this.client = client;
    }
    async loadEvents(): Promise<void> {
        const files = (await glob(`dist/events/**/*.js`)).map(filePath => path.resolve(filePath));

        files.map(async (file: string) => {
            const event : Event = new (await import(file)).default(this.client);

            if (!event.name)
                return delete require.cache[require.resolve(file)] && console.log(`Event ${file.split('/').pop()} n'a pas de nom`);

            const execute = (...args: any[]) => event.execute(...args);

            if (event.once) {
                // @ts-ignore
                this.client.once(event.name, execute);
            } else {
                // @ts-ignore
                this.client.on(event.name, execute);
            }

            console.log(`Event ${file.split('/').pop()} chargé`);

            return delete require.cache[require.resolve(file)];
        });
    }

}