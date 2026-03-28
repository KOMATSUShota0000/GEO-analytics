package com.geo.analytics.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Component
public final class StrictSchemaValidator {
    private static final int MAX_SUMMARY = 8000;
    private static final int MAX_ARRAY_ITEM = 2000;
    private static final int MAX_ARRAY_SIZE = 48;
    private static final Set<String> REQUIRED_KEYS = Set.of(
        "masked_summary",
        "brand_mention_snippets",
        "concrete_facts",
        "pii_redacted");
    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
        "ignore previous",
        "disregard",
        "system prompt",
        "<|",
        "|>",
        "```",
        "${",
        "<script",
        "override instructions",
        "jailbreak");
    private final ObjectMapper objectMapper;

    public StrictSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String validateToCanonicalJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new StructuredExtractValidationException("empty_json");
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new StructuredExtractValidationException("json_parse");
        }
        if (!root.isObject()) {
            throw new StructuredExtractValidationException("not_object");
        }
        var names = new TreeSet<String>();
        root.fieldNames().forEachRemaining(names::add);
        if (!names.equals(new TreeSet<>(REQUIRED_KEYS))) {
            throw new StructuredExtractValidationException("key_mismatch");
        }
        var summary = requireText(root, "masked_summary");
        validateStringField("masked_summary", summary, MAX_SUMMARY);
        validateArrayOfStrings(root, "brand_mention_snippets");
        validateArrayOfStrings(root, "concrete_facts");
        var flag = root.get("pii_redacted");
        if (flag == null || !flag.isBoolean()) {
            throw new StructuredExtractValidationException("pii_flag");
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new StructuredExtractValidationException("serialize");
        }
    }

    private static String requireText(JsonNode root, String field) {
        var n = root.get(field);
        if (n == null || !n.isTextual()) {
            throw new StructuredExtractValidationException("field_" + field);
        }
        return n.asText();
    }

    private static void validateArrayOfStrings(JsonNode root, String field) {
        var n = root.get(field);
        if (n == null || !n.isArray()) {
            throw new StructuredExtractValidationException("array_" + field);
        }
        if (n.size() > MAX_ARRAY_SIZE) {
            throw new StructuredExtractValidationException("array_size_" + field);
        }
        for (Iterator<JsonNode> it = n.elements(); it.hasNext();) {
            var el = it.next();
            if (!el.isTextual()) {
                throw new StructuredExtractValidationException("array_item_type_" + field);
            }
            validateStringField(field, el.asText(), MAX_ARRAY_ITEM);
        }
    }

    private static void validateStringField(String fieldName, String value, int maxLen) {
        if (value.length() > maxLen) {
            throw new StructuredExtractValidationException("len_" + fieldName);
        }
        var lower = value.toLowerCase(Locale.ROOT);
        for (var f : FORBIDDEN_SUBSTRINGS) {
            if (lower.contains(f)) {
                throw new StructuredExtractValidationException("forbidden_token_" + fieldName);
            }
        }
        for (var i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            if (c < 32 || c == 127) {
                throw new StructuredExtractValidationException("control_" + fieldName);
            }
        }
    }
}
