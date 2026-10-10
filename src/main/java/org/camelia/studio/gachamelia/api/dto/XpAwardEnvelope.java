package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record XpAwardEnvelope(Progression progression, ApiUser user) {
    public record Progression(
            int gain,
            @JsonProperty("rank_ups") List<Integer> rankUps,
            @JsonProperty("new_constellations") int newConstellations,
            int quarter
    ) {
    }
}
