package org.camelia.studio.gachamelia.api.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityTrackingApiTransportTest {
    private static final ApiTransportRequest REQUEST = new ApiTransportRequest(
            "GET",
            URI.create("https://example.test/api/discord-servers/1/catalogue"),
            Map.of(),
            null
    );

    @Test
    void isAvailableBeforeAnyCall() {
        assertThat(new AvailabilityTrackingApiTransport(request -> new ApiTransportResponse(200, "")).isAvailable()).isTrue();
    }

    @Test
    void serverErrorsMarkTheApiUnavailableUntilTheNextAnswer() throws Exception {
        int[] statusCode = {503};
        AvailabilityTrackingApiTransport transport = new AvailabilityTrackingApiTransport(
                request -> new ApiTransportResponse(statusCode[0], "")
        );

        transport.send(REQUEST);
        assertThat(transport.isAvailable()).isFalse();

        statusCode[0] = 404;
        transport.send(REQUEST);
        assertThat(transport.isAvailable()).isTrue();
    }

    @Test
    void networkFailuresMarkTheApiUnavailable() {
        AvailabilityTrackingApiTransport transport = new AvailabilityTrackingApiTransport(request -> {
            throw new IOException("connection refused");
        });

        assertThatThrownBy(() -> transport.send(REQUEST)).isInstanceOf(IOException.class);
        assertThat(transport.isAvailable()).isFalse();
    }
}
