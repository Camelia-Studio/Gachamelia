package org.camelia.studio.gachamelia.api;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerUpsertRequest;
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
        apiClient.upsertServer(new DiscordServerUpsertRequest(guild.getId(), guild.getName(), guild.getIconId()));
        apiClient.refreshEmojis(emojiSnapshotService.serverSnapshot(guild.getId(), guild.getEmojis()));
        CatalogueEnvelope envelope = apiClient.getCatalogue(guild.getId());
        validateCatalogue(guild.getId(), envelope);
        catalogueCache.put(guild.getId(), envelope);
        return envelope;
    }

    public UserEnvelope ensureUser(String guildId, String userDiscordId) {
        return apiClient.ensureUser(guildId, userDiscordId);
    }

    public UserEnvelope ensureStaffUser(String guildId, String userDiscordId) {
        return apiClient.ensureStaffUser(guildId, userDiscordId);
    }

    private void validateCatalogue(String guildId, CatalogueEnvelope envelope) {
        if (envelope.catalogue().ranks().isEmpty()) {
            throw new ApiException(409, "rank_catalogue_empty", "Rank catalogue empty for guild " + guildId);
        }
        if (envelope.catalogue().roles().isEmpty()) {
            throw new ApiException(409, "role_catalogue_empty", "Role catalogue empty for guild " + guildId);
        }
        if (envelope.catalogue().elements().isEmpty()) {
            throw new ApiException(409, "element_catalogue_empty", "Element catalogue empty for guild " + guildId);
        }
    }
}
