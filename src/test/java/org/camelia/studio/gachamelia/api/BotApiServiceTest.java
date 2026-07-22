package org.camelia.studio.gachamelia.api;

import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiServerLifecycle;
import org.camelia.studio.gachamelia.api.dto.ApiServerSettings;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
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

class BotApiServiceTest {
    @Test
    void guildOperationsDelegateToApiClientOnce() {
        StubApiClient apiClient = new StubApiClient(validEnvelope());
        BotApiService service = new BotApiService(apiClient, new EmojiSnapshotService());

        assertThat(service.upsertGuild(guild("guild-1"))).isSameAs(apiClient.serverEnvelope);
        assertThat(service.loadCatalogue("guild-1")).isSameAs(apiClient.catalogueEnvelope);
        assertThat(service.refreshGuildEmojis(guild("guild-1"))).isSameAs(apiClient.emojiResponse);
        assertThat(service.deactivateGuild("guild-1")).isSameAs(apiClient.serverEnvelope);

        assertThat(apiClient.upsertServerCalls).isEqualTo(1);
        assertThat(apiClient.getCatalogueCalls).isEqualTo(1);
        assertThat(apiClient.refreshEmojisCalls).isEqualTo(1);
        assertThat(apiClient.deactivateServerCalls).isEqualTo(1);
    }

    @Test
    void loadCatalogueRejectsMissingServer() {
        assertInvalidCatalogue(new CatalogueEnvelope(null, validEnvelope().validation(), validEnvelope().catalogue()), "Catalogue server missing");
    }

    @Test
    void loadCatalogueRejectsMissingServerLifecycle() {
        ApiDiscordServer server = new ApiDiscordServer("guild-1", "Gachamélia", "icon", null, new ApiServerSettings(null, null, null));
        assertInvalidCatalogue(new CatalogueEnvelope(server, validEnvelope().validation(), validEnvelope().catalogue()), "Catalogue server lifecycle missing");
    }

    @Test
    void loadCatalogueRejectsMissingValidation() {
        assertInvalidCatalogue(new CatalogueEnvelope(validEnvelope().server(), null, validEnvelope().catalogue()), "Catalogue validation missing");
    }

    @Test
    void loadCatalogueRejectsMissingValidationErrors() {
        assertInvalidCatalogue(new CatalogueEnvelope(validEnvelope().server(), new ApiCatalogueValidation(true, null, List.of()), validEnvelope().catalogue()), "Catalogue validation errors missing");
    }

    @Test
    void loadCatalogueRejectsMissingValidationWarnings() {
        assertInvalidCatalogue(new CatalogueEnvelope(validEnvelope().server(), new ApiCatalogueValidation(true, List.of(), null), validEnvelope().catalogue()), "Catalogue validation warnings missing");
    }

    @Test
    void loadCatalogueRejectsMissingStatsList() {
        ApiCatalogue catalogue = new ApiCatalogue(List.of(), List.of(), null, List.of());
        assertInvalidCatalogue(new CatalogueEnvelope(validEnvelope().server(), validEnvelope().validation(), catalogue), "Catalogue stats missing");
    }

    @Test
    void loadCatalogueRejectsMissingRolesList() {
        ApiCatalogue catalogue = new ApiCatalogue(List.of(), null, List.of(), List.of());
        assertInvalidCatalogue(new CatalogueEnvelope(validEnvelope().server(), validEnvelope().validation(), catalogue), "Catalogue roles missing");
    }

    private static void assertInvalidCatalogue(CatalogueEnvelope envelope, String message) {
        BotApiService service = new BotApiService(new StubApiClient(envelope), new EmojiSnapshotService());

        assertThatThrownBy(() -> service.loadCatalogue("guild-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(message);
    }

    private static CatalogueEnvelope validEnvelope() {
        return new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Gachamélia", "icon", new ApiServerLifecycle(true, null, null), new ApiServerSettings(null, null, null)),
                new ApiCatalogueValidation(true, List.of(), List.of()),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
    }

    @Test
    void ensureUserSendsExactlyEmptyObjectThroughApiClient() {
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
        BotApiService service = new BotApiService(apiClient, null);

        UserEnvelope envelope = service.ensureUser("guild-1", "user-1");

        assertThat(envelope.user().discordId()).isEqualTo("user-1");
        assertThat(transport.requests).hasSize(2);
        ApiTransportRequest request = transport.requests.get(1);
        assertThat(request.uri().toString()).isEqualTo("https://example.test/api/discord-servers/guild-1/users/user-1");
        assertThat(request.body()).isEqualTo("{}");
        assertThat(request.body()).doesNotContain("rank_id", "role_id", "element_ids");
    }

    @Test
    void ensureStaffUserSendsExactlyStaffTrueThroughApiClient() {
        CapturingTransport transport = new CapturingTransport(
                new ApiTransportResponse(200, "{\"token_type\":\"Bearer\",\"access_token\":\"token-1\",\"expires_in\":3600}"),
                new ApiTransportResponse(200, """
                        {"user":{"id":1,"discord_id":"user-2","rank":{"id":2,"discord_id":"99","name":"Modérateur","is_staff":true},"role":{"id":3,"name":"Comète"},"elements":[{"id":4,"name":"Ambre"}],"stats":[]}}
                        """)
        );
        ApiConfiguration configuration = new ApiConfiguration("https://example.test", "client", "secret");
        ApiTokenProvider tokenProvider = new ApiTokenProvider(
                configuration,
                transport,
                Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC)
        );
        GachameliaApiClient apiClient = new GachameliaApiClient(configuration, tokenProvider, transport);
        BotApiService service = new BotApiService(apiClient, null);

        UserEnvelope envelope = service.ensureStaffUser("guild-1", "user-2");

        assertThat(envelope.user().discordId()).isEqualTo("user-2");
        assertThat(transport.requests).hasSize(2);
        ApiTransportRequest request = transport.requests.get(1);
        assertThat(request.uri().toString()).isEqualTo("https://example.test/api/discord-servers/guild-1/users/user-2");
        assertThat(request.body()).isEqualTo("{\"staff\":true}");
        assertThat(request.body()).doesNotContain("rank_id", "role_id", "element_ids");
    }

    private static net.dv8tion.jda.api.entities.Guild guild(String guildId) {
        return (net.dv8tion.jda.api.entities.Guild) java.lang.reflect.Proxy.newProxyInstance(
                net.dv8tion.jda.api.entities.Guild.class.getClassLoader(),
                new Class[]{net.dv8tion.jda.api.entities.Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> guildId;
                    case "getName" -> "Gachamelia";
                    case "getIconId" -> "icon";
                    case "getEmojis" -> List.of();
                    case "toString" -> "Guild[" + guildId + "]";
                    default -> switch (method.getReturnType().getName()) {
                        case "long" -> 1L;
                        case "int" -> 0;
                        case "boolean" -> false;
                        default -> null;
                    };
                }
        );
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

    private static final class StubApiClient extends GachameliaApiClient {
        private final CatalogueEnvelope catalogueEnvelope;
        private final DiscordServerEnvelope serverEnvelope = new DiscordServerEnvelope(validEnvelope().server());
        private final EmojiSnapshotResponse emojiResponse = new EmojiSnapshotResponse(new EmojiSnapshotResponse.EmojiCache("server", "guild-1", 0, 0));
        private int upsertServerCalls;
        private int getCatalogueCalls;
        private int refreshEmojisCalls;
        private int deactivateServerCalls;

        private StubApiClient(CatalogueEnvelope catalogueEnvelope) {
            super(new ApiConfiguration("https://example.test", "client", "secret"), null, null);
            this.catalogueEnvelope = catalogueEnvelope;
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope upsertServer(org.camelia.studio.gachamelia.api.dto.DiscordServerUpsertRequest request) {
            upsertServerCalls++;
            return serverEnvelope;
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse refreshEmojis(org.camelia.studio.gachamelia.api.dto.EmojiSnapshotRequest request) {
            refreshEmojisCalls++;
            return emojiResponse;
        }

        @Override
        public CatalogueEnvelope getCatalogue(String guildId) {
            getCatalogueCalls++;
            return catalogueEnvelope;
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope deactivateServer(String guildId) {
            deactivateServerCalls++;
            return serverEnvelope;
        }
    }
}
