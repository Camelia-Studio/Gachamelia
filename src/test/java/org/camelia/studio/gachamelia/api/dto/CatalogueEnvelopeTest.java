package org.camelia.studio.gachamelia.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogueEnvelopeTest {
    @Test
    void parsesServerSettingsAndCatalogue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {
                  "server": {
                    "discord_id": "123",
                    "name": "Dev-Bots",
                    "icon": null,
                    "lifecycle": {
                      "active": true,
                      "last_seen_at": "2026-07-22T14:30:00+00:00",
                      "inactive_at": null
                    },
                    "settings": {
                      "welcome_channel_id": "10",
                      "bye_channel_id": "11",
                      "staff_role_id": "12"
                    }
                  },
                  "validation": {
                    "ready": false,
                    "errors": ["role_catalogue_empty"],
                    "warnings": ["welcome_channel_not_configured"]
                  },
                  "catalogue": {
                    "ranks": [{
                      "id": 1,
                      "discord_id": "99",
                      "name": "Novice",
                      "percentage": 35,
                      "bye_title": "Bye",
                      "is_staff": false,
                      "stats": [{"id": 5, "name": "Force", "percentage": 70}],
                      "welcome_messages": [{"id": 6, "message": "Bienvenue %username%."}],
                      "bye_messages": [{"id": 7, "message": "A bientôt %username%."}]
                    }],
                    "roles": [{
                      "id": 2,
                      "name": "Comète",
                      "percentage": 45,
                      "emoji": {"source": "server", "unicode": null, "id": "20", "name": "comete", "animated": false, "available": true, "markup": "<:comete:20>", "cdn_url": null}
                    }],
                    "stats": [{"id": 5, "name": "Force"}],
                    "elements": [{
                      "id": 3,
                      "name": "Ambre",
                      "emoji": {"source": "unicode", "unicode": "🌘", "id": null, "name": null, "animated": false, "available": true, "markup": "🌘", "cdn_url": null}
                    }]
                  }
                }
                """;

        CatalogueEnvelope envelope = mapper.readValue(json, CatalogueEnvelope.class);

        assertThat(envelope.server().discordId()).isEqualTo("123");
        assertThat(envelope.server().lifecycle().active()).isTrue();
        assertThat(envelope.server().lifecycle().lastSeenAt()).isEqualTo("2026-07-22T14:30:00+00:00");
        assertThat(envelope.server().lifecycle().inactiveAt()).isNull();
        assertThat(envelope.server().settings().welcomeChannelId()).isEqualTo("10");
        assertThat(envelope.server().settings().byeChannelId()).isEqualTo("11");
        assertThat(envelope.server().settings().staffRoleId()).isEqualTo("12");
        assertThat(envelope.validation().ready()).isFalse();
        assertThat(envelope.validation().errors()).containsExactly("role_catalogue_empty");
        assertThat(envelope.validation().warnings()).containsExactly("welcome_channel_not_configured");
        assertThat(envelope.catalogue().ranks()).hasSize(1);
        assertThat(envelope.catalogue().ranks().getFirst().welcomeMessages().getFirst().message())
                .isEqualTo("Bienvenue %username%.");
    }
}
