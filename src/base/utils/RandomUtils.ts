import {Rank} from "../models/Rank";

export async function getRandomRank(): Promise<Rank> {
    const rand = Math.random() * 100;
    let cumulativeChance = 0;
    const ranks = await Rank.findAll();

    if (ranks.length === 0) {
        throw new Error('No ranks found');
    }

    for (const rank of ranks) {
        cumulativeChance += rank.percentage;
        if (rand <= cumulativeChance) {
            return rank as Rank;
        }
    }

    return ranks[0] as Rank;
}