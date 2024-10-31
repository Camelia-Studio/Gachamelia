import {CustomClient} from "../classes/CustomClient";
import {User} from "../models/User";
import {getRandomRank} from "./RandomUtils";
import {Rank} from "../enums/Rank";
import {GuildMember} from "discord.js";

export async function addRole(member: GuildMember, user: User, client: CustomClient): Promise<void> {
    try {
        let rRoleId = client.config.rRole;
        let srRoleId = client.config.srRole;
        let ssrRoleId =  client.config.ssrRole;

        if (user.rank === Rank.R) {
            const rRole = await member.guild.roles.fetch(rRoleId);

            if (rRole) {
                await member.roles.add(rRole);
            }
        } else if (user.rank === Rank.SR) {
            const srRole = await member.guild.roles.fetch(srRoleId);

            if (srRole) {
                await member.roles.add(srRole);
            }
        } else if (user.rank === Rank.SSR) {
            const ssrRole = await member.guild.roles.fetch(ssrRoleId);

            if (ssrRole) {
                await member.roles.add(ssrRole);
            }
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
            }
        })

        if (!user) {
            user = await User.create({
                discordId: member.id,
                rank: getRandomRank()
            });

            await addRole(member, user, client);
            
            count++;
        }
    }

    if (count > 0) {
        console.log(`Création de ${count} nouveaux utilisateurs`);
    } else {
        console.log('Aucun nouvel utilisateur créé');
    }
}