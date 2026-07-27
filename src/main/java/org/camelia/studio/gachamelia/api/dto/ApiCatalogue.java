package org.camelia.studio.gachamelia.api.dto;

import java.util.List;

public record ApiCatalogue(
        List<ApiRank> ranks,
        List<ApiRole> roles,
        List<ApiStat> stats,
        List<ApiElement> elements
) {
}
