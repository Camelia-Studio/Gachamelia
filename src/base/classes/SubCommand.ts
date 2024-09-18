import { ChatInputCommandInteraction } from "discord.js";
import {ISubCommand} from "../interfaces/ISubCommand";
import {CustomClient} from "./CustomClient";
import {ISubCommandOptions} from "../interfaces/ISubCommandOptions";

export class SubCommand implements ISubCommand {
    client: CustomClient;
    name: string;

    constructor(client: CustomClient,options: ISubCommandOptions) {
        this.client = client;
        this.name = options.name;
    }

    execute(interaction: ChatInputCommandInteraction): void {
    }

}