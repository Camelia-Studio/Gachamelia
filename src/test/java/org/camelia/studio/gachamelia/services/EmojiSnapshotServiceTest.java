package org.camelia.studio.gachamelia.services;

import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmojiSnapshotServiceTest {
    @Test
    void createsServerSnapshotFromValues() {
        EmojiSnapshotService service = new EmojiSnapshotService();

        EmojiSnapshotRequest request = service.serverSnapshotFromValues(
                "guild-1",
                List.of(new EmojiSnapshotService.EmojiValue("10", "ambre", false, true, "<:ambre:10>", "https://cdn.test/10.webp"))
        );

        assertThat(request.source()).isEqualTo("server");
        assertThat(request.discordServerId()).isEqualTo("guild-1");
        assertThat(request.emojis()).hasSize(1);
        assertThat(request.emojis().getFirst().id()).isEqualTo("10");
        assertThat(request.emojis().getFirst().available()).isTrue();
    }

    @Test
    void createsBotSnapshotFromValuesWithNoServerId() {
        EmojiSnapshotService service = new EmojiSnapshotService();

        EmojiSnapshotRequest request = service.botSnapshotFromValues(
                List.of(new EmojiSnapshotService.EmojiValue("20", "gachamelia", true, true, "<a:gachamelia:20>", "https://cdn.test/20.webp"))
        );

        assertThat(request.source()).isEqualTo("bot");
        assertThat(request.discordServerId()).isNull();
        assertThat(request.emojis().getFirst().animated()).isTrue();
    }
}
