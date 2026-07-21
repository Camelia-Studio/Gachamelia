package org.camelia.studio.gachamelia.api;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerUpsertRequest;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;
import org.camelia.studio.gachamelia.services.GuildCatalogueCache;

public class BotApiService {
    private final GachameliaApiClient apiClient;
    private final GuildCatalogueCache catalogueCache;
    private final EmojiSnapshotService emojiSnapshotService;

    public BotApiService(GachameliaApiClient apiClient, GuildCatalogueCache catalogueCache, EmojiSnapshotService emojiSnapshotService) {
        this.apiClient = apiClient;
        this.catalogueCache = catalogueCache;
        this.emojiSnapshotService = emojiSnapshotService;
    }

    public CatalogueEnvelope initializeGuild(Guild guild) {
        upsertGuild(guild);
        refreshGuildEmojis(guild);
        CatalogueEnvelope envelope = loadCatalogue(guild.getId());
        catalogueCache.put(guild.getId(), envelope);
        return envelope;
    }

    public DiscordServerEnvelope upsertGuild(Guild guild) {
        return apiClient.upsertServer(new DiscordServerUpsertRequest(
                guild.getId(),
                guild.getName(),
                guild.getIconId()
        ));
    }

    public CatalogueEnvelope loadCatalogue(String guildId) {
        CatalogueEnvelope envelope = apiClient.getCatalogue(guildId);
        validateCatalogue(guildId, envelope);
        return envelope;
    }

    public EmojiSnapshotResponse refreshGuildEmojis(Guild guild) {
        return apiClient.refreshEmojis(
                emojiSnapshotService.serverSnapshot(guild.getId(), guild.getEmojis())
        );
    }

    public DiscordServerEnvelope deactivateGuild(String guildId) {
        return apiClient.deactivateServer(guildId);
    }

    public UserEnvelope ensureUser(String guildId, String userDiscordId) {
        return apiClient.ensureUser(guildId, userDiscordId);
    }

    public UserEnvelope ensureStaffUser(String guildId, String userDiscordId) {
        return apiClient.ensureStaffUser(guildId, userDiscordId);
    }

    private void validateCatalogue(String guildId, CatalogueEnvelope envelope) {
        if (envelope == null) {
            throw new ApiException(502, "catalogue_missing", "Catalogue response missing for guild " + guildId);
        }
        if (envelope.server() == null) {
            throw new ApiException(502, "catalogue_server_missing", "Catalogue server missing for guild " + guildId);
        }
        if (envelope.server().lifecycle() == null) {
            throw new ApiException(502, "catalogue_server_lifecycle_missing", "Catalogue server lifecycle missing for guild " + guildId);
        }
        if (envelope.validation() == null) {
            throw new ApiException(502, "catalogue_validation_missing", "Catalogue validation missing for guild " + guildId);
        }
        if (envelope.validation().errors() == null) {
            throw new ApiException(502, "catalogue_validation_errors_missing", "Catalogue validation errors missing for guild " + guildId);
        }
        if (envelope.validation().warnings() == null) {
            throw new ApiException(502, "catalogue_validation_warnings_missing", "Catalogue validation warnings missing for guild " + guildId);
        }
        if (envelope.catalogue() == null) {
            throw new ApiException(502, "catalogue_payload_missing", "Catalogue payload missing for guild " + guildId);
        }
        if (envelope.catalogue().ranks() == null) {
            throw new ApiException(502, "catalogue_ranks_missing", "Catalogue ranks missing for guild " + guildId);
        }
        if (envelope.catalogue().roles() == null) {
            throw new ApiException(502, "catalogue_roles_missing", "Catalogue roles missing for guild " + guildId);
        }
        if (envelope.catalogue().stats() == null) {
            throw new ApiException(502, "catalogue_stats_missing", "Catalogue stats missing for guild " + guildId);
        }
        if (envelope.catalogue().elements() == null) {
            throw new ApiException(502, "catalogue_elements_missing", "Catalogue elements missing for guild " + guildId);
        }
    }
}
