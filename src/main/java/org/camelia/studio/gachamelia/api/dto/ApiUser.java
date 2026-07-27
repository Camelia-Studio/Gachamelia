package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ApiUser(
        long id,
        @JsonProperty("discord_id") String discordId,
        ApiRankSummary rank,
        ApiRoleSummary role,
        List<ApiElementSummary> elements,
        List<ApiUserStat> stats
) {
    public record ApiRankSummary(long id, @JsonProperty("discord_id") String discordId, String name, @JsonProperty("is_staff") boolean staff) {
    }

    public record ApiRoleSummary(long id, String name) {
    }

    public record ApiElementSummary(long id, String name) {
    }
}
