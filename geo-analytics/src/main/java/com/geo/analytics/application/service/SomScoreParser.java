package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import org.springframework.stereotype.Component;

@Component
public class SomScoreParser {
    private final JsonbOperations jsonbOperations;

    public SomScoreParser(JsonbOperations jsonbOperations) {
        this.jsonbOperations = jsonbOperations;
    }

    public SomScoreData parse(String rawAiResponseJson) {
        return jsonbOperations.deserialize(rawAiResponseJson, SomScoreData.class);
    }
}
