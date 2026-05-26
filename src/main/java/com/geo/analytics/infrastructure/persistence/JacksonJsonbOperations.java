package com.geo.analytics.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JacksonJsonbOperations implements JsonbOperations {
    private final ObjectMapper objectMapper;

    public JacksonJsonbOperations(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String serialize(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new JsonbSerializationException(
                "JSON serialization failed for type: " + value.getClass().getName(), jsonProcessingException);
        }
    }

    @Override
    public <T> T deserialize(String rawJson, Class<T> targetType) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("rawJson must not be blank");
        }
        String extractedJsonObject = extractJsonObject(rawJson);
        try {
            return objectMapper.readValue(extractedJsonObject, targetType);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new JsonbSerializationException(
                "JSON deserialization failed for type: " + targetType.getName() + ", input: " + extractedJsonObject,
                jsonProcessingException);
        }
    }

    private String extractJsonObject(String rawText) {
        int startIndex = rawText.indexOf('{');
        int endIndex = rawText.lastIndexOf('}');
        if (startIndex == -1 || endIndex == -1 || endIndex < startIndex) {
            throw new JsonbSerializationException(
                "No valid JSON object found in input: " + rawText, null);
        }
        return rawText.substring(startIndex, endIndex + 1);
    }
}
