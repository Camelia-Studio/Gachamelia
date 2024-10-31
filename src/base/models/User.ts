import { Model, DataTypes, Sequelize } from 'sequelize';
import { Rank } from '../enums/Rank';

export class User extends Model {
    declare id: number;
    declare discordId: string;
    declare rank: Rank;
    declare createdAt: Date;
    declare updatedAt: Date;

    public static initModel(sequelize: Sequelize): void {
        User.init({
            id: {
                type: DataTypes.INTEGER,
                autoIncrement: true,
                primaryKey: true
            },
            discordId: {
                type: DataTypes.STRING,
                allowNull: false,
                unique: true
            },
            rank: {
                type: DataTypes.STRING,
                allowNull: false
            },
            createdAt: DataTypes.DATE,
            updatedAt: DataTypes.DATE
        }, {
            sequelize,
            tableName: 'users'
        });
    }
}