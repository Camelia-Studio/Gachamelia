package org.camelia.studio.gachamelia.api;

import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.api.http.ApiTransport;
import org.camelia.studio.gachamelia.api.http.ApiTransportRequest;
import org.camelia.studio.gachamelia.api.http.ApiTransportResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BotApiServiceTest {
    @Test
    void ensureUserSurfaceReturnsApiUser() {
        CapturingTransport transport = new CapturingTransport(
                new ApiTransportResponse(200, "{\"token_type\":\"Bearer\",\"access_token\":\"token-1\",\"expires_in\":3600}"),
                new ApiTransportResponse(200, """
                        {"user":{"id":1,"discord_id":"user-1","rank":{"id":2,"discord_id":"99","name":"Novice","is_staff":false},"role":{"id":3,"name":"Comète"},"elements":[{"id":4,"name":"Ambre"}],"stats":[]}}
                        """)
        );
        ApiConfiguration configuration = new ApiConfiguration("https://example.test", "client", "secret");
        ApiTokenProvider tokenProvider = new ApiTokenProvider(
                configuration,
                transport,
                Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC)
        );
        GachameliaApiClient apiClient = new GachameliaApiClient(configuration, tokenProvider, transport);
        BotApiService service = new BotApiService(apiClient, null, null);

        UserEnvelope envelope = service.ensureUser("guild-1", "user-1");

        assertThat(envelope.user().discordId()).isEqualTo("user-1");
        assertThat(transport.requests).hasSize(2);
        assertThat(transport.requests.get(1).uri().toString())
                .isEqualTo("https://example.test/api/discord-servers/guild-1/users/user-1");
    }

    static class CapturingTransport implements ApiTransport {
        private final List<ApiTransportResponse> responses;
        private final List<ApiTransportRequest> requests = new ArrayList<>();
        private int index;

        CapturingTransport(ApiTransportResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public ApiTransportResponse send(ApiTransportRequest request) {
            requests.add(request);
            return responses.get(index++);
        }
    }
}
