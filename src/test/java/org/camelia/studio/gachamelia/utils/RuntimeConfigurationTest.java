package org.camelia.studio.gachamelia.utils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigurationTest {
    @Test
    void usesDocumentedDefaults() {
        RuntimeConfiguration configuration = RuntimeConfiguration.parse(null, null, null);

        assertThat(configuration.catalogueRefreshInterval()).isEqualTo(Duration.ofMinutes(5));
        assertThat(configuration.memberSyncConcurrency()).isEqualTo(4);
        assertThat(configuration.syncBotMembers()).isFalse();
    }

    @Test
    void parsesConfiguredValues() {
        RuntimeConfiguration configuration = RuntimeConfiguration.parse("12", "7", "TRUE");

        assertThat(configuration.catalogueRefreshInterval()).isEqualTo(Duration.ofMinutes(12));
        assertThat(configuration.memberSyncConcurrency()).isEqualTo(7);
        assertThat(configuration.syncBotMembers()).isTrue();
    }

    @Test
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> RuntimeConfiguration.parse("0", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CATALOGUE_REFRESH_INTERVAL_MINUTES");
        assertThatThrownBy(() -> RuntimeConfiguration.parse(null, "-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MEMBER_SYNC_CONCURRENCY");
        assertThatThrownBy(() -> RuntimeConfiguration.parse(null, null, "yes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SYNC_BOT_MEMBERS");
    }
}
