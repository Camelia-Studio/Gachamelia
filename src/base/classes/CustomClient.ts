import {IConfig} from "../interfaces/IConfig";
import {ICustomClient} from "../interfaces/ICustomClient";
import {Client, Collection, GatewayIntentBits} from "discord.js";
import {Handler} from "./Handler";
import {Command} from "./Command";
import {SubCommand} from "./SubCommand";
import {Config} from "./LoadConfig";
import {Database} from "./Database";
import {IDatabaseConfig} from "../interfaces/IDatabaseConfig";
import {Dialect} from "sequelize";
import {User} from "../models/User";
import {Rank} from "../models/Rank";

export class CustomClient extends Client implements ICustomClient {
    config: IConfig;
    handler: Handler;
    commands: Collection<string, Command>;
    subCommands: Collection<string, SubCommand>;
    cooldowns: Collection<string, Collection<string, number>>;
    database: Database;

    constructor() {
        super({
            intents: Object.keys(GatewayIntentBits).map((a: string) => {
                return GatewayIntentBits[a as keyof typeof GatewayIntentBits];
            }),
        })
        // On ajoute le support de dotenv pour la config plutôt qu'un fichier JSON
        require('dotenv').config();

        // On charge dynamiquement la config
        this.config = Config.load();

        const dbConfig: IDatabaseConfig = {
            dialect: this.config.dbDialect as Dialect,
            host: this.config.dbHost,
            port: this.config.dbPort,
            username: this.config.dbUsername,
            password: this.config.dbPassword,
            database: this.config.dbName
        };

        this.database = new Database(dbConfig);
        this.handler = new Handler(this);
        this.commands = new Collection();
        this.subCommands = new Collection();
        this.cooldowns = new Collection();
    }
    async init(): Promise<void> {
        await this.database.connect();

        Rank.initModel(this.database.getSequelize());
        User.initModel(this.database.getSequelize());


        Rank.hasMany(User, {
            foreignKey: 'rankId',
            as: 'users'
        });
        User.belongsTo(Rank, {
            foreignKey: 'rankId',
            as: 'rank'
        });

        await this.database.sync({
            alter: true
        });
        await this.LoadHandlers();
        this.login(this.config.token).catch(console.error);
    }

    async LoadHandlers(): Promise<void> {
        await this.handler.loadEvents();
        await this.handler.loadCommands();
    }

}