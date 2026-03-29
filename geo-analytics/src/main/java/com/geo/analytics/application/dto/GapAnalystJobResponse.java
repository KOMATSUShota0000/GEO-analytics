package com.geo.analytics.application.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

public record GapAnalystJobResponse(String diagnosticMessage, List<String> recommendedActions) {
    public static GapAnalystJobResponse parseStructuredJson(String json, ObjectMapper objectMapper)
        throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);
        String dm = root.path("diagnostic_message").asText("");
        JsonNode arr = root.path("recommended_actions");
        var list = new ArrayList<String>();
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                list.add(n.asText(""));
            }
        }
        while (list.size() < 3) {
            list.add("");
        }
        return new GapAnalystJobResponse(dm, List.of(list.get(0), list.get(1), list.get(2)));
    }
}
