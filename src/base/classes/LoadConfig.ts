import dotenv from 'dotenv';
import type { IConfig } from '../interfaces/IConfig';

export class Config {
    public static load(): IConfig {
        dotenv.config();
        return new Proxy<IConfig>({} as IConfig, {
            get: (_, prop: string): string | undefined =>
                process.env[prop.split(/(?=[A-Z])/).join('_').toUpperCase()]
        });
    }
}