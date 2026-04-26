package com.geo.analytics.infrastructure.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.StrictMath;

/**
 * Compresses AI visibility provider {@code organic_results} into GEO citation evidence for LLM RAG:
 * ranks 1–20 include snippet; 21–100 title+url only.
 */
public final class SerpRagHybridFormatter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ROWS = 100;
    private static final int SNIPPET_MAX_RANK = 20;

    private SerpRagHybridFormatter() {
    }

    /**
     * One JSON object per line (newline-delimited), UTF-8 logical model.
     */
    public static String format(JsonNode root) {
        if (root == null || root.isNull()) {
            return "【GEO可視性エビデンス（1〜100）】\n（引用エビデンス: パース不能）\n";
        }
        JsonNode organic = root.get("organic_results");
        if (organic == null || !organic.isArray() || organic.isEmpty()) {
            return "【GEO可視性エビデンス（1〜100）】\n（引用エビデンス: 0件）\n";
        }
        StringBuilder sb = new StringBuilder(organic.size() * 120);
        sb.append("【GEO可視性エビデンス（1〜100）】\n");
        int n = StrictMath.min(MAX_ROWS, organic.size());
        for (int i = 0; i < n; i++) {
            JsonNode o = organic.get(i);
            JsonNode posNode = o.get("position");
            int rank = (posNode != null && !posNode.isNull()) ? posNode.asInt(i + 1) : i + 1;
            String title = textField(o, "title");
            String url = textField(o, "link");
            String snippet = textField(o, "snippet");
            boolean includeSnippet = (posNode != null && !posNode.isNull())
                ? posNode.asInt() <= SNIPPET_MAX_RANK
                : i < SNIPPET_MAX_RANK;
            ObjectNode row = JsonNodeFactory.instance.objectNode();
            row.put("rank", rank);
            row.put("title", squashWs(title));
            row.put("url", squashWs(url));
            if (includeSnippet) {
                row.put("snippet", squashWs(snippet));
            }
            try {
                sb.append(MAPPER.writeValueAsString(row)).append('\n');
            } catch (Exception e) {
                sb.append("{\"rank\":").append(rank).append(",\"error\":\"format\"}\n");
            }
        }
        return sb.toString();
    }

    private static String textField(JsonNode o, String name) {
        if (o == null || !o.has(name) || o.get(name).isNull()) {
            return "";
        }
        return o.get(name).asText("");
    }

    private static String squashWs(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
