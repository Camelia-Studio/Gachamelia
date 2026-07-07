package org.camelia.studio.gachamelia.api;

import org.camelia.studio.gachamelia.api.http.ApiTransport;
import org.camelia.studio.gachamelia.api.http.ApiTransportRequest;
import org.camelia.studio.gachamelia.api.http.ApiTransportResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

class ApiTokenProviderTest {
    @Test
    void requestsTokenWithBasicAuthAndCachesIt() {
        CapturingTransport transport = new CapturingTransport(new ApiTransportResponse(
                200,
                "{\"token_type\":\"Bearer\",\"access_token\":\"token-1\",\"expires_in\":3600}"
        ));
        ApiTokenProvider provider = new ApiTokenProvider(
                new ApiConfiguration("https://example.test/gachamelia", "client", "secret"),
                transport,
                Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(provider.getToken()).isEqualTo("token-1");
        assertThat(provider.getToken()).isEqualTo("token-1");
        assertThat(transport.requests).hasSize(1);

        ApiTransportRequest request = transport.requests.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().toString()).isEqualTo("https://example.test/gachamelia/api/auth/token");
        assertThat(request.headers()).containsEntry(
                "Authorization",
                "Basic " + Base64.getEncoder().encodeToString("client:secret".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void invalidateForcesNextTokenRequest() {
        CapturingTransport transport = new CapturingTransport(
                new ApiTransportResponse(200, "{\"token_type\":\"Bearer\",\"access_token\":\"token-1\",\"expires_in\":3600}"),
                new ApiTransportResponse(200, "{\"token_type\":\"Bearer\",\"access_token\":\"token-2\",\"expires_in\":3600}")
        );
        ApiTokenProvider provider = new ApiTokenProvider(
                new ApiConfiguration("https://example.test", "client", "secret"),
                transport,
                Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(provider.getToken()).isEqualTo("token-1");
        provider.invalidate();
        assertThat(provider.getToken()).isEqualTo("token-2");
        assertThat(transport.requests).hasSize(2);
    }

    @Test
    void throwsApiExceptionWithErrorCodeWhenTokenRequestReturnsNon200() {
        CapturingTransport transport = new CapturingTransport(new ApiTransportResponse(401, "{\"error\":\"invalid_client\"}"));
        ApiTokenProvider provider = new ApiTokenProvider(
                new ApiConfiguration("https://example.test", "client", "secret"),
                transport,
                Clock.systemDefaultZone()
        );

        ApiException exception = assertThrows(ApiException.class, provider::getToken);

        assertThat(exception.statusCode()).isEqualTo(401);
        assertThat(exception.errorCode()).isEqualTo("invalid_client");
    }

    @Test
    void throwsApiExceptionWhenTokenResponseIsMissingAccessTokenOrExpiresInIsInvalid() {
        CapturingTransport transport = new CapturingTransport(
                new ApiTransportResponse(200, "{\"token_type\":\"Bearer\",\"expires_in\":0}"),
                new ApiTransportResponse(200, "{\"token_type\":\"Bearer\",\"access_token\":\"token-2\",\"expires_in\":3600}")
        );
        ApiTokenProvider provider = new ApiTokenProvider(
                new ApiConfiguration("https://example.test", "client", "secret"),
                transport,
                Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC)
        );

        ApiException firstFailure = assertThrows(ApiException.class, provider::getToken);
        assertThat(firstFailure.statusCode()).isEqualTo(200);
        assertThat(firstFailure.errorCode()).isEqualTo("invalid_token_response");

        assertThat(provider.getToken()).isEqualTo("token-2");
        assertThat(transport.requests).hasSize(2);
    }

    @Test
    void interruptsCurrentThreadWhenTransportIsInterrupted() {
        ApiTokenProvider provider = new ApiTokenProvider(
                new ApiConfiguration("https://example.test", "client", "secret"),
                new InterruptingTransport(),
                Clock.systemDefaultZone()
        );

        ApiException exception = assertThrows(ApiException.class, provider::getToken);

        assertThat(exception.statusCode()).isEqualTo(0);
        assertThat(exception.errorCode()).isEqualTo("token_request_failed");
        assertThat(Thread.interrupted()).isTrue();
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

    static class InterruptingTransport implements ApiTransport {
        @Override
        public ApiTransportResponse send(ApiTransportRequest request) throws InterruptedException {
            throw new InterruptedException("simulated interruption");
        }
    }
}
