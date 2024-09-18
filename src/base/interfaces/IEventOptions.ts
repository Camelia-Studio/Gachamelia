import {ClientEvents} from "discord.js";

export interface IEventOptions {
    name: keyof ClientEvents;
    description: string;
    once: boolean;
}