package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.api.dto.ApiMessage;
import org.camelia.studio.gachamelia.api.dto.ApiRank;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

public class CatalogueMessageService {
    private final RandomGenerator random;

    public CatalogueMessageService(RandomGenerator random) {
        this.random = random;
    }

    public Optional<ApiRank> findRank(CatalogueEnvelope envelope, long rankId) {
        return envelope.catalogue().ranks().stream()
                .filter(rank -> rank.id() == rankId)
                .findFirst();
    }

    public Optional<String> randomWelcomeMessage(CatalogueEnvelope envelope, long rankId) {
        return findRank(envelope, rankId).flatMap(rank -> randomMessage(rank.welcomeMessages()));
    }

    public Optional<String> randomByeMessage(CatalogueEnvelope envelope, long rankId) {
        return findRank(envelope, rankId).flatMap(rank -> randomMessage(rank.byeMessages()));
    }

    private Optional<String> randomMessage(List<ApiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(messages.get(random.nextInt(messages.size())).message());
    }
}
