import { Model, DataTypes, Sequelize } from 'sequelize';
import {User} from "./User";

export class Rank extends Model {
    declare id: number;
    declare name: string;
    declare discordId: string;
    declare percentage: number;
    declare createdAt: Date;
    declare updatedAt: Date;
    declare users: User[];

    public static initModel(sequelize: Sequelize): void {
        Rank.init({
            id: {
                type: DataTypes.INTEGER,
                autoIncrement: true,
                primaryKey: true
            },
            name: {
                type: DataTypes.STRING,
                allowNull: false
            },
            discordId: {
                type: DataTypes.STRING,
                allowNull: false,
                unique: true
            },
            percentage: {
                type: DataTypes.INTEGER,
                allowNull: false
            },
            createdAt: DataTypes.DATE,
            updatedAt: DataTypes.DATE,
        }, {
            sequelize,
            tableName: 'ranks'
        });
    }
}