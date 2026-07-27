package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuildCatalogueCacheTest {
    @Test
    void storesCatalogueByGuildId() {
        GuildCatalogueCache cache = new GuildCatalogueCache();
        CatalogueEnvelope envelope = envelope("guild-1");

        cache.put("guild-1", envelope);

        assertThat(cache.find("guild-1")).containsSame(envelope);
        assertThat(cache.require("guild-1")).isSameAs(envelope);
    }

    @Test
    void requireFailsClearlyWhenCatalogueIsMissing() {
        GuildCatalogueCache cache = new GuildCatalogueCache();

        assertThatThrownBy(() -> cache.require("guild-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("guild-1");
    }

    @Test
    void putReturnsPreviousCatalogueAtomically() {
        GuildCatalogueCache cache = new GuildCatalogueCache();
        CatalogueEnvelope first = envelope("guild-1", false);
        CatalogueEnvelope second = envelope("guild-1", true);

        assertThat(cache.put("guild-1", first)).isNull();
        assertThat(cache.put("guild-1", second)).isSameAs(first);
        assertThat(cache.find("guild-1")).containsSame(second);
    }

    @Test
    void findReadyOnlyReturnsReadyCatalogue() {
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", envelope("guild-1", false));

        assertThat(cache.findReady("guild-1")).isEmpty();
        assertThatThrownBy(() -> cache.requireReady("guild-1"))
                .isInstanceOfSatisfying(GuildNotReadyException.class, exception -> {
                    assertThat(exception.guildId()).isEqualTo("guild-1");
                    assertThat(exception).hasMessageContaining("guild-1");
                });

        cache.put("guild-1", envelope("guild-1", true));
        assertThat(cache.findReady("guild-1")).containsSame(cache.find("guild-1").orElseThrow());
    }

    @Test
    void removeDeletesStoredCatalogue() {
        GuildCatalogueCache cache = new GuildCatalogueCache();
        cache.put("guild-1", envelope("guild-1"));

        cache.remove("guild-1");

        assertThat(cache.find("guild-1")).isEmpty();
    }

    private CatalogueEnvelope envelope(String guildId) {
        return envelope(guildId, true);
    }

    private CatalogueEnvelope envelope(String guildId, boolean ready) {
        return new CatalogueEnvelope(
                new ApiDiscordServer(guildId, "Guild", null, null),
                new org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation(ready, List.of(), List.of()),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
    }
}
