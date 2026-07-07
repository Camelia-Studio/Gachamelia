package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiServerSettings(
        @JsonProperty("welcome_channel_id") String welcomeChannelId,
        @JsonProperty("bye_channel_id") String byeChannelId,
        @JsonProperty("staff_role_id") String staffRoleId
) {
}
