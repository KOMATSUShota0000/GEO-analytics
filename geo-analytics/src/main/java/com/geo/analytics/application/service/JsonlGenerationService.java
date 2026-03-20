package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.infrastructure.persistence.JsonbSerializationException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class JsonlGenerationService {
    private final ObjectMapper objectMapper;

    public JsonlGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] generateBatchRequestJsonl(String brandName, List<QueryEntity> queryEntities) {
        StringBuilder jsonlBuilder = new StringBuilder();
        for (QueryEntity queryEntity : queryEntities) {
            try {
                Map<String, Object> batchRequestLine =
                    buildSingleBatchRequestLine(brandName, queryEntity);
                jsonlBuilder.append(objectMapper.writeValueAsString(batchRequestLine)).append("\n");
            } catch (JsonProcessingException jsonProcessingException) {
                throw new JsonbSerializationException(
                    "Failed to serialize batch request line for query: " + queryEntity.getId(),
                    jsonProcessingException);
            }
        }
        return jsonlBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> buildSingleBatchRequestLine(
            String brandName, QueryEntity queryEntity) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("generationConfig", generationConfig);
        request.put("contents", List.of(
            Map.of(
                "role", "user",
                "parts", List.of(
                    Map.of("text", buildBrandVisibilityPrompt(
                        brandName, queryEntity.getQueryText()))
                )
            )
        ));
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("key", queryEntity.getId().toString());
        line.put("request", request);
        return line;
    }

    private String buildBrandVisibilityPrompt(String brandName, String userQuery) {
        return """
            You are an AI brand visibility analyzer.
            Brand under evaluation: %s
            User query: %s
            Respond ONLY with valid JSON matching this exact schema with no additional text:
            {"response":"<natural language answer to the query>","brandMentioned":<true|false>,"mentionRank":<integer 1-10 if mentioned, null if not>,"confidenceScore":<float 0.0-1.0>}
            """.formatted(brandName, userQuery);
    }
}
