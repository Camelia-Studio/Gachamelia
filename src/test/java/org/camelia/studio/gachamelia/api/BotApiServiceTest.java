package org.camelia.studio.gachamelia.api;

import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiDiscordServer;
import org.camelia.studio.gachamelia.api.dto.ApiElement;
import org.camelia.studio.gachamelia.api.dto.ApiRank;
import org.camelia.studio.gachamelia.api.dto.ApiRole;
import org.camelia.studio.gachamelia.api.dto.ApiServerSettings;
import org.camelia.studio.gachamelia.api.dto.ApiStat;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;
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
        BotApiService service = new BotApiService(apiClient, null, null);

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
        BotApiService service = new BotApiService(apiClient, null, null);

        UserEnvelope envelope = service.ensureStaffUser("guild-1", "user-2");

        assertThat(envelope.user().discordId()).isEqualTo("user-2");
        assertThat(transport.requests).hasSize(2);
        ApiTransportRequest request = transport.requests.get(1);
        assertThat(request.uri().toString()).isEqualTo("https://example.test/api/discord-servers/guild-1/users/user-2");
        assertThat(request.body()).isEqualTo("{\"staff\":true}");
        assertThat(request.body()).doesNotContain("rank_id", "role_id", "element_ids");
    }

    @Test
    void initializeGuildRejectsMissingCatalogueEnvelope() {
        BotApiService service = new BotApiService(new StubApiClient(null), new GuildCatalogueCache(), new EmojiSnapshotService());

        assertThatThrownBy(() -> service.initializeGuild(guild("guild-1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Catalogue response missing");
    }

    @Test
    void initializeGuildRejectsCatalogueWithoutRanksList() {
        CatalogueEnvelope malformedEnvelope = new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Gachamélia", "icon", new ApiServerSettings(null, null, null)),
                new ApiCatalogue(null, List.of(new ApiRole(2L, "Comète", 100, null)), List.of(new ApiStat(3L, "Force")), List.of(new ApiElement(4L, "Ambre", null)))
        );
        BotApiService service = new BotApiService(new StubApiClient(malformedEnvelope), new GuildCatalogueCache(), new EmojiSnapshotService());

        assertThatThrownBy(() -> service.initializeGuild(guild("guild-1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Catalogue ranks missing");
    }

    @Test
    void initializeGuildRejectsCatalogueWithoutElementsList() {
        CatalogueEnvelope malformedEnvelope = new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Gachamélia", "icon", new ApiServerSettings(null, null, null)),
                new ApiCatalogue(List.of(new ApiRank(1L, "99", "Novice", 100, null, false, List.of(), List.of(), List.of())), List.of(new ApiRole(2L, "Comète", 100, null)), List.of(new ApiStat(3L, "Force")), null)
        );
        BotApiService service = new BotApiService(new StubApiClient(malformedEnvelope), new GuildCatalogueCache(), new EmojiSnapshotService());

        assertThatThrownBy(() -> service.initializeGuild(guild("guild-1")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Catalogue elements missing");
    }

    @Test
    void initializeGuildAcceptsEmptyCatalogue() {
        CatalogueEnvelope emptyEnvelope = new CatalogueEnvelope(
                new ApiDiscordServer("guild-1", "Gachamélia", "icon", new ApiServerSettings(null, null, null)),
                new ApiCatalogue(List.of(), List.of(), List.of(), List.of())
        );
        GuildCatalogueCache cache = new GuildCatalogueCache();
        BotApiService service = new BotApiService(new StubApiClient(emptyEnvelope), cache, new EmojiSnapshotService());

        CatalogueEnvelope envelope = service.initializeGuild(guild("guild-1"));

        assertThat(envelope).isSameAs(emptyEnvelope);
        assertThat(cache.find("guild-1")).containsSame(emptyEnvelope);
    }

    private static net.dv8tion.jda.api.entities.Guild guild(String guildId) {
        return (net.dv8tion.jda.api.entities.Guild) java.lang.reflect.Proxy.newProxyInstance(
                net.dv8tion.jda.api.entities.Guild.class.getClassLoader(),
                new Class[]{net.dv8tion.jda.api.entities.Guild.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> guildId;
                    case "getName" -> "Gachamélia";
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

        private StubApiClient(CatalogueEnvelope catalogueEnvelope) {
            super(new ApiConfiguration("https://example.test", "client", "secret"), null, null);
            this.catalogueEnvelope = catalogueEnvelope;
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope upsertServer(org.camelia.studio.gachamelia.api.dto.DiscordServerUpsertRequest request) {
            return null;
        }

        @Override
        public org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse refreshEmojis(org.camelia.studio.gachamelia.api.dto.EmojiSnapshotRequest request) {
            return null;
        }

        @Override
        public CatalogueEnvelope getCatalogue(String guildId) {
            return catalogueEnvelope;
        }
    }
}
