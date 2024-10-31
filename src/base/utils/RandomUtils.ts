import {Rank} from "../enums/Rank";
import {RankChance} from "../enums/RankChance";

export function getRandomRank(): Rank {
    const rand = Math.random() * 100;
    let cumulativeChance = 0;

    for (const rank of Object.values(Rank)) {
        cumulativeChance += RankChance[rank as keyof typeof RankChance];
        if (rand <= cumulativeChance) {
            return rank as Rank;
        }
    }

    return Rank.R;
}