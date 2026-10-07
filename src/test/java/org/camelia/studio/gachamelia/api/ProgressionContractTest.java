package org.camelia.studio.gachamelia.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camelia.studio.gachamelia.api.dto.ApiCatalogue;
import org.camelia.studio.gachamelia.api.dto.ApiUser;
import org.camelia.studio.gachamelia.api.dto.XpAwardEnvelope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressionContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsPerGuildProgressionAndAwardResponse() throws Exception {
        ApiCatalogue catalogue = mapper.readValue("""
                {"ranks":[],"roles":[],"stats":[],"elements":[],"progression":{
                  "rank_ids":[1,2,3,4,5],"thresholds":[10000,150000,450000,960000],
                  "quarter_percentages":[20,40,60,80],"constellation_threshold":1111111,
                  "message_xp":7,"voice_xp":15,"voice_interval_minutes":5}}
                """, ApiCatalogue.class);
        assertThat(catalogue.progression().configured()).isTrue();
        assertThat(catalogue.progression().voiceIntervalMinutes()).isEqualTo(5);

        XpAwardEnvelope award = mapper.readValue("""
                {"progression":{"gain":7,"rank_ups":[2],"new_constellations":0,"quarter":0},
                 "user":{"id":1,"discord_id":"42","rank":null,"role":null,"elements":[],"stats":[],
                         "progression":{"xp":3,"total_xp":13,"constellations":0}}}
                """, XpAwardEnvelope.class);
        assertThat(award.progression().rankUps()).containsExactly(2);
        assertThat(award.user().progression()).isEqualTo(new ApiUser.Progression(3, 13, 0));
    }
}
