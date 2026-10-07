package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ApiProgressionSettings(
        @JsonProperty("rank_ids") List<Long> rankIds,
        List<Long> thresholds,
        @JsonProperty("quarter_percentages") List<Integer> quarterPercentages,
        @JsonProperty("constellation_threshold") long constellationThreshold,
        @JsonProperty("message_xp") int messageXp,
        @JsonProperty("voice_xp") int voiceXp,
        @JsonProperty("voice_interval_minutes") int voiceIntervalMinutes
) {
    public boolean configured() {
        return rankIds != null && rankIds.size() == 5 && rankIds.stream().allMatch(id -> id != null && id > 0)
                && thresholds != null && thresholds.size() == 4 && constellationThreshold > 0
                && messageXp > 0 && voiceXp > 0 && voiceIntervalMinutes > 0;
    }
}
