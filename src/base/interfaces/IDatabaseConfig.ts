
import {Dialect} from "sequelize";

export interface IDatabaseConfig {
    dialect: Dialect;
    host: string;
    port: number;
    username: string;
    password: string;
    database: string;
    logging?: boolean | ((sql: string, timing?: number) => void);
    poolOptions?: {
        max?: number;
        min?: number;
        acquire?: number;
        idle?: number;
    };
}