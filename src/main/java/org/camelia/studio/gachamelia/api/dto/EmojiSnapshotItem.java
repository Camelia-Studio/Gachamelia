package org.camelia.studio.gachamelia.api.dto;

public record EmojiSnapshotItem(
        String id,
        String name,
        boolean animated,
        boolean available
) {
}
