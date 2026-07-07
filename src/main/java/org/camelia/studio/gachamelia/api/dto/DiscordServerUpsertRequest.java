package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DiscordServerUpsertRequest(
        @JsonProperty("discord_id") String discordId,
        String name,
        String icon
) {
}
