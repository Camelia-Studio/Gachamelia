import {EmbedBuilder, Events, GuildMember} from "discord.js";
import {CustomClient} from "../../base/classes/CustomClient";
import {Event} from "../../base/classes/Event";
import {User} from "../../base/models/User";
import {getRandomRank} from "../../base/utils/RandomUtils";
import {Rank} from "../../base/enums/Rank";
import {addRole} from "../../base/utils/GachaUtils";

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
                rank: getRandomRank()
            });
        }

        await addRole(member, user, this.client);

        const channel = await member.guild.channels.fetch(this.client.config.welcomeChannel);

        if (channel && channel.isTextBased()) {
            // TODO : Remplacer la description par une image générée par le bot
            let embed = new EmbedBuilder()
                .setTitle(`Bienvenue sur le serveur ${member.displayName} !`)
                .setThumbnail(member.displayAvatarURL())
                .setDescription(`Bien joué ! Tu as obtenu le rang : ${Rank[user.rank]}`)
                .setTimestamp(new Date())
                .setColor('#0078DE')
            ;

            await channel.send({embeds: [embed]});
        }
    }
}