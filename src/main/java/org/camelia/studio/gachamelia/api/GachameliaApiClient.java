package org.camelia.studio.gachamelia.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerUpsertRequest;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotRequest;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse;
import org.camelia.studio.gachamelia.api.dto.EnsureUserRequest;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.api.http.ApiTransport;
import org.camelia.studio.gachamelia.api.http.ApiTransportRequest;
import org.camelia.studio.gachamelia.api.http.ApiTransportResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class GachameliaApiClient {
    private final ApiConfiguration configuration;
    private final ApiTokenProvider tokenProvider;
    private final ApiTransport transport;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GachameliaApiClient(ApiConfiguration configuration, ApiTokenProvider tokenProvider, ApiTransport transport) {
        this.configuration = configuration;
        this.tokenProvider = tokenProvider;
        this.transport = transport;
    }

    public DiscordServerEnvelope upsertServer(DiscordServerUpsertRequest request) {
        return sendAuthorized("POST", "/discord-servers", request, DiscordServerEnvelope.class, true);
    }

    public EmojiSnapshotResponse refreshEmojis(EmojiSnapshotRequest request) {
        return sendAuthorized("PUT", "/discord-emojis", request, EmojiSnapshotResponse.class, true);
    }

    public CatalogueEnvelope getCatalogue(String guildId) {
        return sendAuthorized("GET", "/discord-servers/" + encode(guildId) + "/catalogue", null, CatalogueEnvelope.class, true);
    }

    public UserEnvelope ensureUser(String guildId, String userDiscordId) {
        return sendAuthorized("PUT", userPath(guildId, userDiscordId), EnsureUserRequest.normal(), UserEnvelope.class, true);
    }

    public UserEnvelope ensureStaffUser(String guildId, String userDiscordId) {
        return sendAuthorized("PUT", userPath(guildId, userDiscordId), EnsureUserRequest.staff(), UserEnvelope.class, true);
    }

    private String userPath(String guildId, String userDiscordId) {
        return "/discord-servers/" + encode(guildId) + "/users/" + encode(userDiscordId);
    }

    private <T> T sendAuthorized(String method, String path, Object body, Class<T> responseType, boolean allowTokenRetry) {
        ApiTransportResponse response = send(method, path, body, tokenProvider.getToken());
        if (response.statusCode() == 401 && allowTokenRetry) {
            tokenProvider.invalidate();
            return sendAuthorized(method, path, body, responseType, false);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(response.statusCode(), readErrorCode(response.body()), response.body());
        }
        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (Exception exception) {
            throw new ApiException(response.statusCode(), "invalid_json_response", exception.getMessage());
        }
    }

    private ApiTransportResponse send(String method, String path, Object body, String token) {
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", "Bearer " + token);
            headers.put("Accept", "application/json");
            String requestBody = null;
            if (body != null) {
                headers.put("Content-Type", "application/json");
                requestBody = objectMapper.writeValueAsString(body);
            }
            return transport.send(new ApiTransportRequest(method, configuration.apiUri(path), headers, requestBody));
        } catch (ApiException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "request_failed", exception.getMessage());
        } catch (Exception exception) {
            throw new ApiException(0, "request_failed", exception.getMessage());
        }
    }

    private String readErrorCode(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            return json.path("error").asText("api_error");
        } catch (Exception exception) {
            return "api_error";
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
