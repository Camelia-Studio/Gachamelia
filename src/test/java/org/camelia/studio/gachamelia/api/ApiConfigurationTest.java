package org.camelia.studio.gachamelia.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiConfigurationTest {
    @Test
    void normalizesBaseUrlWithoutTrailingSlash() {
        ApiConfiguration configuration = new ApiConfiguration(
                "https://example.test/gachamelia/",
                "gachamelia-bot",
                "secret"
        );

        assertThat(configuration.baseUrl()).isEqualTo("https://example.test/gachamelia");
        assertThat(configuration.apiUri("/discord-servers").toString())
                .isEqualTo("https://example.test/gachamelia/api/discord-servers");
    }

    @Test
    void rejectsMissingRequiredValues() {
        assertThatThrownBy(() -> new ApiConfiguration("", "client", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API_BASE_URL");
        assertThatThrownBy(() -> new ApiConfiguration("https://example.test", "", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API_CLIENT_ID");
        assertThatThrownBy(() -> new ApiConfiguration("https://example.test", "client", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API_CLIENT_SECRET");
    }
}
