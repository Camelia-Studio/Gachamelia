package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.models.ByeMessage;
import org.camelia.studio.gachamelia.models.Rank;
import org.camelia.studio.gachamelia.models.WelcomeMessage;
import org.camelia.studio.gachamelia.repositories.RankRepository;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RankService {
    private static RankService instance;

    public static RankService getInstance() {
        if (instance == null) {
            instance = new RankService();
        }

        return instance;
    }

    public Rank getOrCreateRank(String name, String discordId, int percentage) {
        Rank rank = RankRepository.getInstance().findByName(name);

        if (rank == null) {
            rank = new Rank(discordId, name, percentage);
            RankRepository.getInstance().save(rank);
        }

        return rank;
    }

    public Rank getOrCreateRank(String name, String discordId, int percentage, String byeTitle) {
        Rank rank = RankRepository.getInstance().findByName(name);

        if (rank == null) {
            rank = new Rank(discordId, name, percentage, byeTitle);
            RankRepository.getInstance().save(rank);
        }

        return rank;
    }

    public List<Rank> getAllRanks() {
        return RankRepository.getInstance().findAll();
    }

    public Rank getRandomRank() {
        List<Rank> ranks = RankRepository.getInstance().findAll();

        int percentage = ThreadLocalRandom.current().nextInt(100);
        int cumulativePercentage = 0;

        for (Rank rank : ranks) {
            cumulativePercentage += rank.getPercentage();
            if (percentage <= cumulativePercentage) {
                return rank;
            }
        }

        // Ne devrait jamais arriver
        return null;
    }

    public WelcomeMessage getRandomWelcomeMessage(Rank rank) {
        return RankRepository.getInstance().getRandomWelcomeMessage(rank);
    }

    public ByeMessage getRandomByeMessage(Rank rank) {
        return RankRepository.getInstance().getRandomByeMessage(rank);
    }
}
