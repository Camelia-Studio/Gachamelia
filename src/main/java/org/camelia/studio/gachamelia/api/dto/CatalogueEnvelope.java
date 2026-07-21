package org.camelia.studio.gachamelia.api.dto;

import java.util.List;

public record CatalogueEnvelope(
        ApiDiscordServer server,
        ApiCatalogueValidation validation,
        ApiCatalogue catalogue
) {
    public CatalogueEnvelope(ApiDiscordServer server, ApiCatalogue catalogue) {
        this(server, new ApiCatalogueValidation(true, List.of(), List.of()), catalogue);
    }
}
