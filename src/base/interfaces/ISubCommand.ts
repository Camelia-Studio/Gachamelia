import {CustomClient} from "../classes/CustomClient";
import {ChatInputCommandInteraction} from "discord.js";

export interface ISubCommand {
    client: CustomClient;
    name: string;

    execute(interaction: ChatInputCommandInteraction): void;
}