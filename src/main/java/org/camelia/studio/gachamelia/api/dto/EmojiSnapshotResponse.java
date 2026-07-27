package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmojiSnapshotResponse(EmojiCache cache) {
    public record EmojiCache(String source, @JsonProperty("cache_key") String cacheKey, int received, int available) {
    }
}
