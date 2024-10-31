import {Sequelize} from "sequelize-typescript";
import {Options} from "sequelize";
import {IDatabaseConfig} from "../interfaces/IDatabaseConfig";

export class Database {
    private readonly sequelize: Sequelize;

    constructor(config: IDatabaseConfig) {
        const options: Options = {
            dialect: config.dialect,
            host: config.host,
            port: config.port,
            username: config.username,
            password: config.password,
            database: config.database,
            logging: false,
            pool: {
                max: config.poolOptions?.max ?? 5,
                min: config.poolOptions?.min ?? 0,
                acquire: config.poolOptions?.acquire ?? 30000,
                idle: config.poolOptions?.idle ?? 10000
            }
        };

        this.sequelize = new Sequelize(options);
    }

    public getSequelize(): Sequelize {
        return this.sequelize;
    }

    public async connect(): Promise<void> {
        try {
            await this.sequelize.authenticate();
            console.log('Connexion à la base de données établie avec succès.');
        } catch (error) {
            console.error('Impossible de se connecter à la base de données:', error);
            throw error;
        }
    }

    public async close(): Promise<void> {
        try {
            await this.sequelize.close();
            console.log('Connexion à la base de données fermée avec succès.');
        } catch (error) {
            console.error('Erreur lors de la fermeture de la connexion:', error);
            throw error;
        }
    }

    public async sync(options?: { force?: boolean; alter?: boolean }): Promise<void> {
        await this.sequelize.sync(options);
    }
}