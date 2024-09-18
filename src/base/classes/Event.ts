import {ClientEvents} from "discord.js";
import {IEvent} from "../interfaces/IEvent";
import {CustomClient} from "./CustomClient";
import {IEventOptions} from "../interfaces/IEventOptions";

export class Event implements IEvent {
    client: CustomClient;
    name: keyof ClientEvents;
    description: string;
    once: boolean;

    constructor(client: CustomClient, options: IEventOptions) {
        this.client = client;
        this.name = options.name;
        this.description = options.description;
        this.once = options.once;
    }
    execute(...args: any[]): void {};


}