package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiDiscordServer(
        @JsonProperty("discord_id") String discordId,
        String name,
        String icon,
        ApiServerLifecycle lifecycle,
        ApiServerSettings settings
) {
    public ApiDiscordServer(String discordId, String name, String icon, ApiServerSettings settings) {
        this(discordId, name, icon, new ApiServerLifecycle(true, null, null), settings);
    }
}
