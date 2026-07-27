package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmojiSnapshotRequest(
        String source,
        @JsonProperty("discord_server_id") String discordServerId,
        List<EmojiSnapshotItem> emojis
) {
}
