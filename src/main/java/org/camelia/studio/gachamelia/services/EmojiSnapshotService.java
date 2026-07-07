package org.camelia.studio.gachamelia.services;

import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotItem;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotRequest;

import java.util.List;

public class EmojiSnapshotService {
    public EmojiSnapshotRequest serverSnapshot(String guildId, List<? extends RichCustomEmoji> emojis) {
        return serverSnapshotFromValues(
                guildId,
                emojis.stream()
                        .map(emoji -> new EmojiValue(
                                emoji.getId(),
                                emoji.getName(),
                                emoji.isAnimated(),
                                emoji.isAvailable(),
                                emoji.getAsMention(),
                                emoji.getImageUrl()
                        ))
                        .toList()
        );
    }

    public EmojiSnapshotRequest botSnapshot(List<? extends ApplicationEmoji> emojis) {
        return botSnapshotFromValues(
                emojis.stream()
                        .map(emoji -> new EmojiValue(
                                emoji.getId(),
                                emoji.getName(),
                                emoji.isAnimated(),
                                true,
                                emoji.getAsMention(),
                                emoji.getImageUrl()
                        ))
                        .toList()
        );
    }

    public EmojiSnapshotRequest serverSnapshotFromValues(String guildId, List<EmojiValue> emojis) {
        return new EmojiSnapshotRequest("server", guildId, emojis.stream().map(this::toEmojiSnapshotItem).toList());
    }

    public EmojiSnapshotRequest botSnapshotFromValues(List<EmojiValue> emojis) {
        return new EmojiSnapshotRequest("bot", null, emojis.stream().map(this::toEmojiSnapshotItem).toList());
    }

    private EmojiSnapshotItem toEmojiSnapshotItem(EmojiValue emoji) {
        return new EmojiSnapshotItem(emoji.id(), emoji.name(), emoji.animated(), emoji.available());
    }

    public record EmojiValue(String id, String name, boolean animated, boolean available, String markup, String cdnUrl) {
    }
}
