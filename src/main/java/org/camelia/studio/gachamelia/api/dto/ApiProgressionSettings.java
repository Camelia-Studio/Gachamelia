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
        @JsonProperty("voice_interval_minutes") int voiceIntervalMinutes,
        @JsonProperty("message_channel_ids") List<String> messageChannelIds,
        @JsonProperty("voice_channel_ids") List<String> voiceChannelIds
) {
    public boolean configured() {
        return rankIds != null && rankIds.size() == 5 && rankIds.stream().allMatch(id -> id != null && id > 0)
                && thresholds != null && thresholds.size() == 4 && constellationThreshold > 0
                && messageXp > 0 && voiceXp > 0 && voiceIntervalMinutes > 0;
    }

    public boolean allows(String source, String channelId) {
        List<String> ids = "message".equals(source) ? messageChannelIds : "voice".equals(source) ? voiceChannelIds : null;
        return ids != null && ids.contains(channelId);
    }
}
