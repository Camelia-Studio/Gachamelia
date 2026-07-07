package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiMessage;
import org.camelia.studio.gachamelia.api.dto.ApiRank;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueMessageServiceTest {
    @Test
    void selectsWelcomeAndByeMessagesFromRank() {
        CatalogueMessageService service = new CatalogueMessageService(new FixedRandom(0));
        CatalogueEnvelope envelope = envelope();

        assertThat(service.randomWelcomeMessage(envelope, 1)).contains("Bienvenue");
        assertThat(service.randomByeMessage(envelope, 1)).contains("A bientôt");
    }

    @Test
    void returnsEmptyWhenRankOrMessagesAreMissing() {
        CatalogueMessageService service = new CatalogueMessageService(new FixedRandom(0));
        CatalogueEnvelope envelope = envelope();

        assertThat(service.randomWelcomeMessage(envelope, 99)).isEmpty();
        assertThat(service.randomByeMessage(envelope, 99)).isEmpty();
    }

    private CatalogueEnvelope envelope() {
        ApiRank rank = new ApiRank(
                1,
                "99",
                "Novice",
                35,
                "Bye",
                false,
                List.of(),
                List.of(new ApiMessage(1, "Bienvenue")),
                List.of(new ApiMessage(2, "A bientôt"))
        );
        return new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Guild", null, null),
                new ApiCatalogue(List.of(rank), List.of(), List.of(), List.of())
        );
    }

    static class FixedRandom implements RandomGenerator {
        private final int value;

        FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
