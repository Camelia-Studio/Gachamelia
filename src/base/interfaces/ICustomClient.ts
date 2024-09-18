import {IConfig} from "./IConfig";
import {Command} from "../classes/Command";
import {Collection} from "discord.js";
import {SubCommand} from "../classes/SubCommand";

export interface ICustomClient {
    config: IConfig;
    commands: Collection<string, Command>;
    subCommands: Collection<string, SubCommand>;
    cooldowns: Collection<string, Collection<string, number>>;

    init(): void;
    LoadHandlers(): void;
}