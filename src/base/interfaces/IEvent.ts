import {CustomClient} from "../classes/CustomClient";
import {ClientEvents} from "discord.js";

export interface IEvent {
    client: CustomClient;
    name: keyof ClientEvents;
    description: string;
    once: boolean;

    execute(...args: any[]): void;
}