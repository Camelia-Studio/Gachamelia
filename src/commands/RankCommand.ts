import {Command} from "../base/classes/Command";
import {CustomClient} from "../base/classes/CustomClient";
import {Category} from "../base/enums/Category";
import {EmbedBuilder, GuildMember, PermissionsBitField, SlashCommandUserOption} from "discord.js";
import {User} from "../base/models/User";
import {Rank} from "../base/models/Rank";
import {getRandomRank} from "../base/utils/RandomUtils";

export class PingCommand extends Command {
    constructor(client: CustomClient) {
        super(client, {
            name: 'rank',
            description: 'Permet de voir son rang',
            category: Category.UTILITIES,
            options: [
                new SlashCommandUserOption()
                    .setName('user')
                    .setDescription('L\'utilisateur dont vous voulez voir le rang')
                    .setRequired(false)
            ],
            default_member_permissions: PermissionsBitField.Flags.UseApplicationCommands,
            dm_permission: false,
            cooldown: 1
        });
    }

    async execute(interaction: any): Promise<void> {
        let discordUser = (interaction.options.getMember('user') || interaction.member) as GuildMember;

        let user = await User.findOne(
            {
                where: {
                    discordId: discordUser.id
                },
                include: [{
                    model: Rank,
                    as: 'rank'
                }],
            }
        );

        if (!user) {
            user = await User.create({
                discordId: discordUser.id,
                rankId: (await getRandomRank()).id
            });
        }

        // TODO: Remplacer la description par une image générée par le bot
        let embed = new EmbedBuilder()
            .setTitle(`Rang de ${discordUser.displayName}`)
            .setThumbnail(discordUser.displayAvatarURL())
            .setDescription(`Cet utilisateur est de rang : ${user.rank.name}`)
            .setTimestamp(new Date())
            .setColor('#0078DE')
        ;

        await interaction.reply({embeds: [embed]});
    }
}