package com.geo.analytics.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ScoreBreakdownSerializationTest {

    @Test
    void serializesV13AxisFieldsInSnakeCase() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        // content50 + technical20 + authority30(=core20+localSub10) = final100 の代表値。
        ScoreBreakdown breakdown = new ScoreBreakdown(
                40.0d, 25.0d, 20.0d, 100.0d,
                50.0d, 20.0d, 30.0d, 20.0d, 10.0d, 0.0d, "V13_GEO4AXIS");
        String json = mapper.writeValueAsString(breakdown);
        assertThat(json)
                // 新3軸＋権威小計＋版が露出していること
                .contains("\"content_total\":50.0")
                .contains("\"technical_total\":20.0")
                .contains("\"authority_total\":30.0")
                .contains("\"authority_third_party_core\":20.0")
                .contains("\"authority_local_meo_sub\":10.0")
                .contains("\"authority_wikipedia_kg_bonus\":0.0")
                .contains("\"calculation_version\":\"V13_GEO4AXIS\"")
                // 後方互換の旧フィールドも従来通り残ること
                .contains("\"ai_audit_total\":40.0")
                .contains("\"final_score\":100.0");
    }

    @Test
    void emptyHasNullVersionAndZeros() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ScoreBreakdown.empty());
        assertThat(json)
                .contains("\"content_total\":0.0")
                .contains("\"calculation_version\":null");
    }
}
