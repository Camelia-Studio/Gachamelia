import {EmbedBuilder, Events, GuildMember} from "discord.js";
import {CustomClient} from "../../base/classes/CustomClient";
import {Event} from "../../base/classes/Event";
import {User} from "../../base/models/User";
import {getRandomRank} from "../../base/utils/RandomUtils";
import {addRole} from "../../base/utils/GachaUtils";
import {Rank} from "../../base/models/Rank";

export class GuildMemberJoin extends Event {
    constructor(client: CustomClient) {
        super(client, {
            name: Events.GuildMemberAdd,
            once: false,
            description: 'Event se déclenchant lorsqu\'un utilisateur rejoint le serveur'
        });
    }

    async execute(member: GuildMember): Promise<void> {

        let user = await User.findOne({
            where: {
                discordId: member.id
            }
        });

        if (!user) {
            user = await User.create({
                discordId: member.id,
                rankId: (await getRandomRank()).id
            }, {
                include: [{
                    model: Rank,
                    as: 'rank'
                }]
            });
        }

        await addRole(member, user);

        const channel = await member.guild.channels.fetch(this.client.config.welcomeChannel);

        if (channel && channel.isTextBased()) {
            // TODO : Remplacer la description par une image générée par le bot
            let embed = new EmbedBuilder()
                .setTitle(`Bienvenue sur le serveur ${member.displayName} !`)
                .setThumbnail(member.displayAvatarURL())
                .setDescription(`Bien joué ! Tu as obtenu le rang : ${user.rank.name}`)
                .setTimestamp(new Date())
                .setColor('#0078DE')
            ;

            await channel.send({embeds: [embed]});
        }
    }
}