import {
    Model,
    DataTypes,
    Sequelize,
    ForeignKey,
    NonAttribute,
    InferAttributes, InferCreationAttributes, CreationOptional, Association
} from 'sequelize';
import {Rank} from "./Rank";

export class User extends Model<
    InferAttributes<User>,
    InferCreationAttributes<User>> {
    declare id: CreationOptional<number>;
    declare discordId: string;
    declare rankId: ForeignKey<number>;
    declare rank: NonAttribute<Rank>;
    declare createdAt: CreationOptional<Date>;
    declare updatedAt: CreationOptional<Date>;

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
            rankId: {
                type: DataTypes.INTEGER,
                allowNull: false,
                references: {
                    model: Rank,
                    key: 'id'
                }
            },
            createdAt: DataTypes.DATE,
            updatedAt: DataTypes.DATE
        }, {
            sequelize,
            tableName: 'users'
        });
    }



    declare static associations: {
        rank: Association<User, Rank>;
    };
}