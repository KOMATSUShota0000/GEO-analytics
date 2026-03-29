package com.geo.analytics.infrastructure.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GapAnalystOutputSchema {
    private GapAnalystOutputSchema() {}

    public static Map<String, Object> batchGenerationConfig() {
        var generationConfig = new LinkedHashMap<String, Object>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", rootSchemaMap());
        return generationConfig;
    }

    private static Map<String, Object> strictObjectProps(Map<String, Object> properties, List<String> required) {
        var root = new LinkedHashMap<String, Object>();
        root.put("type", "OBJECT");
        root.put("properties", properties);
        root.put("required", required);
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> rootSchemaMap() {
        var actionsItems = Map.of("type", "STRING");
        var actionsArray = new LinkedHashMap<String, Object>();
        actionsArray.put("type", "ARRAY");
        actionsArray.put("items", actionsItems);
        actionsArray.put("minItems", 3);
        actionsArray.put("maxItems", 3);
        var properties = new LinkedHashMap<String, Object>();
        properties.put("diagnostic_message", Map.of("type", "STRING"));
        properties.put("recommended_actions", actionsArray);
        var required = new ArrayList<String>();
        required.add("diagnostic_message");
        required.add("recommended_actions");
        return strictObjectProps(properties, required);
    }
}
