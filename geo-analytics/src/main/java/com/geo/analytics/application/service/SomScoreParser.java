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
        return jsonbOperations.deserialize(sanitizeMarkdownArtifacts(rawAiResponseJson), SomScoreData.class);
    }

    private String sanitizeMarkdownArtifacts(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw == null ? "" : raw.trim();
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            } else {
                text = text.replaceFirst("^`{3,}\\w*", "").trim();
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
        }
        text = text.replaceAll("(?i)```\\s*json\\s*", "").replace("```", "");
        return text.trim();
    }
}
