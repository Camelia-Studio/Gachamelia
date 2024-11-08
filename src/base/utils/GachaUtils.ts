import {CustomClient} from "../classes/CustomClient";
import {User} from "../models/User";
import {getRandomRank} from "./RandomUtils";
import {GuildMember} from "discord.js";
import {Rank} from "../models/Rank";

export async function addRole(member: GuildMember, user: User): Promise<void> {
    try {
        let userRank = await Rank.findOne({
            where: {
                id: user.rankId
            }
        });

        if (!userRank) {
            return;
        }

        const discordRank = await member.guild.roles.fetch(userRank.discordId);
        if (discordRank) {
            await member.roles.add(userRank.discordId);
        }
    } catch (e: Error | any) {
        console.log(`Impossible d'ajouter les rôles à ${member.displayName}`);
        console.error(e.message);
    }
}

export async function createAllUsers(client: CustomClient): Promise<void> {
    let count = 0;
    const guild = await client.guilds.fetch(client.config.guildId)
    const members = await guild.members.fetch();
    for (const member  of members.values()) {
        let user = await User.findOne({
            where: {
                discordId: member.id
            },
            include: {
                model: Rank,
                as: 'rank'
            }
        })

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

            await addRole(member, user);
            
            count++;
        }
    }

    if (count > 0) {
        console.log(`Création de ${count} nouveaux utilisateurs`);
    } else {
        console.log('Aucun nouvel utilisateur créé');
    }
}