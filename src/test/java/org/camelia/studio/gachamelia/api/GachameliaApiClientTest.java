package org.camelia.studio.gachamelia.api;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GachameliaApiClientTest {
    @Test
    void ensureUserSendsExactlyEmptyObject() {
        CapturingTransport transport = new CapturingTransport(
                token("token-1"),
                user("42")
        );
        GachameliaApiClient client = client(transport);

        client.ensureUser("guild-1", "42");

        ApiTransportRequest request = transport.requests.get(1);
        assertThat(request.method()).isEqualTo("PUT");
        assertThat(request.uri().toString()).isEqualTo("https://example.test/api/discord-servers/guild-1/users/42");
        assertThat(request.body()).isEqualTo("{}");
        assertThat(request.headers()).containsEntry("Authorization", "Bearer token-1");
    }

    @Test
    void ensureStaffUserSendsExactlyStaffTrue() {
        CapturingTransport transport = new CapturingTransport(
                token("token-1"),
                user("42")
        );
        GachameliaApiClient client = client(transport);

        client.ensureStaffUser("guild-1", "42");

        ApiTransportRequest request = transport.requests.get(1);
        assertThat(request.body()).isEqualTo("{\"staff\":true}");
    }

    @Test
    void unauthorizedInvalidatesTokenAndRetriesOnce() {
        CapturingTransport transport = new CapturingTransport(
                token("token-1"),
                new ApiTransportResponse(401, "{\"error\":\"unauthorized\"}"),
                token("token-2"),
                user("42")
        );
        GachameliaApiClient client = client(transport);

        client.ensureUser("guild-1", "42");

        assertThat(transport.requests).hasSize(4);
        assertThat(transport.requests.get(1).headers()).containsEntry("Authorization", "Bearer token-1");
        assertThat(transport.requests.get(3).headers()).containsEntry("Authorization", "Bearer token-2");
    }

    @Test
    void sendInterruptedExceptionRestoresInterruptFlagAndWrapsInRequestFailedApiException() {
        GachameliaApiClient client = client(new InterruptingAfterTokenTransport(token("token-1")));

        try {
            assertThatThrownBy(() -> client.ensureUser("guild-1", "42"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(exception -> {
                        ApiException apiException = (ApiException) exception;
                        assertThat(apiException.statusCode()).isEqualTo(0);
                        assertThat(apiException.errorCode()).isEqualTo("request_failed");
                    });
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static GachameliaApiClient client(ApiTransport transport) {
        ApiConfiguration configuration = new ApiConfiguration("https://example.test", "client", "secret");
        ApiTokenProvider tokenProvider = new ApiTokenProvider(
                configuration,
                transport,
                Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC)
        );
        return new GachameliaApiClient(configuration, tokenProvider, transport);
    }

    private static ApiTransportResponse token(String token) {
        return new ApiTransportResponse(200, "{\"token_type\":\"Bearer\",\"access_token\":\"" + token + "\",\"expires_in\":3600}");
    }

    private static ApiTransportResponse user(String discordId) {
        return new ApiTransportResponse(200, """
                {"user":{"id":1,"discord_id":"%s","rank":{"id":2,"discord_id":"99","name":"Novice","is_staff":false},"role":{"id":3,"name":"Comète"},"elements":[{"id":4,"name":"Ambre"}],"stats":[]}}
                """.formatted(discordId));
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

    static class InterruptingAfterTokenTransport implements ApiTransport {
        private final ApiTransportResponse tokenResponse;
        private boolean firstCallDone;

        InterruptingAfterTokenTransport(ApiTransportResponse tokenResponse) {
            this.tokenResponse = tokenResponse;
        }

        @Override
        public ApiTransportResponse send(ApiTransportRequest request) throws InterruptedException {
            if (!firstCallDone) {
                firstCallDone = true;
                return tokenResponse;
            }
            throw new InterruptedException("simulated interruption");
        }
    }
}
