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

    private CatalogueEnvelope envelope(String guildId) {
        return new CatalogueEnvelope(
                new ApiDiscordServer(guildId, "Guild", null, null),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
    }
}
