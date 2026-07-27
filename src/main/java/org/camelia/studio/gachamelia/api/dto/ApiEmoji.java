package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiEmoji(
        String source,
        String unicode,
        String id,
        String name,
        boolean animated,
        boolean available,
        String markup,
        @JsonProperty("cdn_url") String cdnUrl
) {
}
