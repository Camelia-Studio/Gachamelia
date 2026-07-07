package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiDiscordServer(
        @JsonProperty("discord_id") String discordId,
        String name,
        String icon,
        ApiServerSettings settings
) {
}
