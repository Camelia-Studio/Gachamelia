package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class GuildCatalogueCache {
    private final Map<String, CatalogueEnvelope> catalogues = new ConcurrentHashMap<>();

    public void put(String guildId, CatalogueEnvelope envelope) {
        catalogues.put(guildId, envelope);
    }

    public Optional<CatalogueEnvelope> find(String guildId) {
        return Optional.ofNullable(catalogues.get(guildId));
    }

    public void remove(String guildId) {
        catalogues.remove(guildId);
    }

    public CatalogueEnvelope require(String guildId) {
        CatalogueEnvelope envelope = catalogues.get(guildId);
        if (envelope == null) {
            throw new IllegalStateException("Catalogue missing for guild " + guildId);
        }
        return envelope;
    }
}
