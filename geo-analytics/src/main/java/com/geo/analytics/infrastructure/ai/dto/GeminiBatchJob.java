package com.geo.analytics.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeminiBatchJob {
    private static final ObjectMapper ADDITIONAL_TO_NODE = new ObjectMapper();

    @JsonProperty("name")
    private String name;

    @JsonProperty("state")
    private String state;

    @JsonProperty("outputConfig")
    private JsonNode outputConfig;

    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String state() {
        if (state != null && !state.isBlank()) {
            return state;
        }
        String extracted = extractNestedString(additionalProperties, "batch", "state");
        if (extracted != null && !extracted.isBlank()) {
            return extracted;
        }
        Object meta = additionalProperties.get("metadata");
        if (meta instanceof Map<?, ?> metaMap) {
            Object s = metaMap.get("state");
            if (s != null) {
                String asText = s.toString();
                if (!asText.isBlank()) {
                    return asText;
                }
            }
        }
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public JsonNode outputConfig() {
        if (outputConfig != null && !outputConfig.isNull()) {
            return outputConfig;
        }
        Object direct = additionalProperties.get("outputConfig");
        if (direct != null) {
            return ADDITIONAL_TO_NODE.valueToTree(direct);
        }
        Object batch = additionalProperties.get("batch");
        if (batch instanceof Map<?, ?> batchMap) {
            Object oc = batchMap.get("outputConfig");
            if (oc != null) {
                return ADDITIONAL_TO_NODE.valueToTree(oc);
            }
        }
        return JsonNodeFactory.instance.objectNode();
    }

    public void setOutputConfig(JsonNode outputConfig) {
        this.outputConfig = outputConfig;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String propertyName, Object propertyValue) {
        additionalProperties.put(propertyName, propertyValue);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    private static String extractNestedString(Map<String, Object> root, String key1, String key2) {
        Object first = root.get(key1);
        if (!(first instanceof Map<?, ?> map)) {
            return null;
        }
        Object second = map.get(key2);
        return second == null ? null : second.toString();
    }
}
