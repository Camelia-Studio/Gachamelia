package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ApiRank(
        long id,
        @JsonProperty("discord_id") String discordId,
        String name,
        int percentage,
        @JsonProperty("bye_title") String byeTitle,
        @JsonProperty("is_staff") boolean staff,
        List<ApiRankStat> stats,
        @JsonProperty("welcome_messages") List<ApiMessage> welcomeMessages,
        @JsonProperty("bye_messages") List<ApiMessage> byeMessages
) {
}
