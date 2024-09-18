import {CustomClient} from "../classes/CustomClient";
import {AutocompleteInteraction, ChatInputCommandInteraction} from "discord.js";
import {Category} from "../enums/Category";

export interface ICommand {
    client: CustomClient;
    name: string;
    description: string;
    category: Category;
    options: object;
    default_member_permissions: bigint;
    dm_permissions: boolean;
    cooldown: number;

    execute(interaction: ChatInputCommandInteraction): void;
    autocomplete(interaction: AutocompleteInteraction): void;
}