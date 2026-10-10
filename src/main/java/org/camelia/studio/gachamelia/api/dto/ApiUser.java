package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiUser(
        long id,
        @JsonProperty("discord_id") String discordId,
        ApiRankSummary rank,
        ApiRoleSummary role,
        List<ApiElementSummary> elements,
        List<ApiUserStat> stats,
        Progression progression
) {
    public ApiUser(long id, String discordId, ApiRankSummary rank, ApiRoleSummary role,
                   List<ApiElementSummary> elements, List<ApiUserStat> stats) {
        this(id, discordId, rank, role, elements, stats, null);
    }

    public record Progression(long xp, @JsonProperty("total_xp") long totalXp, int constellations) {
    }
    public record ApiRankSummary(long id, @JsonProperty("discord_id") String discordId, String name, @JsonProperty("is_staff") boolean staff) {
    }

    public record ApiRoleSummary(long id, String name) {
    }

    public record ApiElementSummary(long id, String name) {
    }
}
