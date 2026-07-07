package org.camelia.studio.gachamelia.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.gachamelia.api.dto.AuthTokenResponse;
import org.camelia.studio.gachamelia.api.http.ApiTransport;
import org.camelia.studio.gachamelia.api.http.ApiTransportRequest;
import org.camelia.studio.gachamelia.api.http.ApiTransportResponse;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

public class ApiTokenProvider {
    private static final long EXPIRATION_MARGIN_SECONDS = 60;

    private final ApiConfiguration configuration;
    private final ApiTransport transport;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public ApiTokenProvider(ApiConfiguration configuration, ApiTransport transport, Clock clock) {
        this.configuration = configuration;
        this.transport = transport;
        this.clock = clock;
    }

    public synchronized String getToken() {
        if (token != null && Instant.now(clock).isBefore(expiresAt.minusSeconds(EXPIRATION_MARGIN_SECONDS))) {
            return token;
        }

        ApiTransportResponse response;
        try {
            response = transport.send(new ApiTransportRequest(
                    "POST",
                    configuration.apiUri("/auth/token"),
                    Map.of("Authorization", basicAuthorization()),
                    null
            ));
        } catch (Exception exception) {
            throw new ApiException(0, "token_request_failed", exception.getMessage());
        }

        if (response.statusCode() != 200) {
            throw new ApiException(response.statusCode(), readErrorCode(response.body()), "Unable to obtain API token");
        }

        try {
            AuthTokenResponse tokenResponse = objectMapper.readValue(response.body(), AuthTokenResponse.class);
            token = tokenResponse.accessToken();
            expiresAt = Instant.now(clock).plusSeconds(tokenResponse.expiresIn());
            return token;
        } catch (Exception exception) {
            throw new ApiException(response.statusCode(), "invalid_token_response", exception.getMessage());
        }
    }

    public synchronized void invalidate() {
        token = null;
        expiresAt = Instant.EPOCH;
    }

    private String basicAuthorization() {
        String credentials = configuration.clientId() + ":" + configuration.clientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String readErrorCode(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            return json.path("error").asText("api_error");
        } catch (Exception exception) {
            return "api_error";
        }
    }
}
