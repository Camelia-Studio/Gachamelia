import CustomClient from "../classes/CustomClient";
import {Events} from "discord.js";

export default interface IEvent {
    client: CustomClient;
    name: Events;
    description: string;
    once: boolean;

    execute(...args: any[]): void;
}