package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiServerLifecycle(
        boolean active,
        @JsonProperty("last_seen_at") String lastSeenAt,
        @JsonProperty("inactive_at") String inactiveAt
) {
}
