import IConfig from "../interfaces/IConfig";
import ICustomClient from "../interfaces/ICustomClient";
import {Client} from "discord.js";
import Handler from "./Handler";

export default class CustomClient extends Client implements ICustomClient {
    config: IConfig;
    handler: Handler;
    constructor() {
        super({
            intents: [],
        })

        this.config = require(`${process.cwd()}/data/config.json`);
        this.handler = new Handler(this);
    }
    async init(): Promise<void> {
        await this.LoadHandlers();
        this.login(this.config.token).catch(console.error);
    }

    async LoadHandlers(): Promise<void> {
        await this.handler.loadEvents();
    }

}