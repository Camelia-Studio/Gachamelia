package org.camelia.studio.gachamelia.api.dto;

import java.util.List;

public record ApiCatalogueValidation(boolean ready, List<String> errors, List<String> warnings) {
}
