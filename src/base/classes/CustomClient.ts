import {IConfig} from "../interfaces/IConfig";
import {ICustomClient} from "../interfaces/ICustomClient";
import {Client, Collection, GatewayIntentBits} from "discord.js";
import {Handler} from "./Handler";
import {Command} from "./Command";
import {SubCommand} from "./SubCommand";

export class CustomClient extends Client implements ICustomClient {
    config: IConfig;
    handler: Handler;
    commands: Collection<string, Command>;
    subCommands: Collection<string, SubCommand>;
    cooldowns: Collection<string, Collection<string, number>>;

    constructor() {
        super({
            intents: Object.keys(GatewayIntentBits).map((a: string) => {
                return GatewayIntentBits[a as keyof typeof GatewayIntentBits];
            }),
        })

        this.config = require(`${process.cwd()}/data/config.json`);
        this.handler = new Handler(this);
        this.commands = new Collection();
        this.subCommands = new Collection();
        this.cooldowns = new Collection();
    }
    async init(): Promise<void> {
        await this.LoadHandlers();
        this.login(this.config.token).catch(console.error);
    }

    async LoadHandlers(): Promise<void> {
        await this.handler.loadEvents();
        await this.handler.loadCommands();
    }

}