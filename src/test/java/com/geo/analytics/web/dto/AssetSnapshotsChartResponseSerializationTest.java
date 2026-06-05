package com.geo.analytics.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetSnapshotsChartResponseSerializationTest {

    @Test
    void serializesSnakeCaseKeys() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AssetSnapshotsChartResponse response =
                new AssetSnapshotsChartResponse(
                        List.of(new AssetSnapshotChartPoint("2026-05-01", 82.5, 7L, "V13_GEO4AXIS")));
        String json = mapper.writeValueAsString(response);
        assertThat(json)
                .contains("\"snapshot_date\":\"2026-05-01\"")
                .contains("\"geo_readiness_score\":82.5")
                .contains("\"local_trust_count\":7")
                .contains("\"calculation_version\":\"V13_GEO4AXIS\"")
                .contains("\"data\":");
    }
}
