package org.camelia.studio.gachamelia.api;

import net.dv8tion.jda.api.entities.Guild;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogueValidation;
import org.camelia.studio.gachamelia.api.dto.CatalogueEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerEnvelope;
import org.camelia.studio.gachamelia.api.dto.DiscordServerUpsertRequest;
import org.camelia.studio.gachamelia.api.dto.EmojiSnapshotResponse;
import org.camelia.studio.gachamelia.api.dto.UserEnvelope;
import org.camelia.studio.gachamelia.services.EmojiSnapshotService;

import java.util.ArrayList;
import java.util.List;

public class BotApiService {
    private final GachameliaApiClient apiClient;
    private final EmojiSnapshotService emojiSnapshotService;

    public BotApiService(GachameliaApiClient apiClient, EmojiSnapshotService emojiSnapshotService) {
        this.apiClient = apiClient;
        this.emojiSnapshotService = emojiSnapshotService;
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
        return normalizeCatalogue(guildId, envelope);
    }

    public EmojiSnapshotResponse refreshGuildEmojis(Guild guild) {
        return apiClient.refreshEmojis(
                emojiSnapshotService.serverSnapshot(guild.getId(), guild.getEmojis())
        );
    }

    public void deactivateGuild(String guildId) {
        apiClient.deactivateServer(guildId);
    }

    public UserEnvelope ensureUser(String guildId, String userDiscordId) {
        return apiClient.ensureUser(guildId, userDiscordId);
    }

    public UserEnvelope ensureStaffUser(String guildId, String userDiscordId) {
        return apiClient.ensureStaffUser(guildId, userDiscordId);
    }

    private CatalogueEnvelope normalizeCatalogue(String guildId, CatalogueEnvelope envelope) {
        if (envelope == null) {
            throw new ApiException(502, "catalogue_missing", "Catalogue response missing for guild " + guildId);
        }
        if (envelope.server() == null) {
            throw new ApiException(502, "catalogue_server_missing", "Catalogue server missing for guild " + guildId);
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

        ApiCatalogueValidation validation = envelope.validation();
        if (validation == null) {
            List<String> errors = new ArrayList<>();
            if (envelope.catalogue().ranks().isEmpty()) {
                errors.add("rank_catalogue_empty");
            }
            if (envelope.catalogue().roles().isEmpty()) {
                errors.add("role_catalogue_empty");
            }
            if (envelope.catalogue().elements().isEmpty()) {
                errors.add("element_catalogue_empty");
            }
            return new CatalogueEnvelope(
                    envelope.server(),
                    new ApiCatalogueValidation(errors.isEmpty(), List.copyOf(errors), List.of()),
                    envelope.catalogue()
            );
        }
        if (validation.errors() == null || validation.warnings() == null) {
            return new CatalogueEnvelope(
                    envelope.server(),
                    new ApiCatalogueValidation(
                            validation.ready(),
                            validation.errors() == null ? List.of() : List.copyOf(validation.errors()),
                            validation.warnings() == null ? List.of() : List.copyOf(validation.warnings())
                    ),
                    envelope.catalogue()
            );
        }

        return envelope;
    }
}
