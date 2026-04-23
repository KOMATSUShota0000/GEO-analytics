package com.geo.analytics.infrastructure.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SerpRagHybridFormatterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void rank20IncludesSnippetRank21Omits() throws Exception {
        String json = """
            {
              "organic_results": [
                {"position": 1, "title": "T1", "link": "https://a.example", "snippet": "S1"},
                {"position": 20, "title": "T20", "link": "https://b.example", "snippet": "S20"},
                {"position": 21, "title": "T21", "link": "https://c.example", "snippet": "DROP"}
              ]
            }
            """;
        JsonNode root = MAPPER.readTree(json);
        String out = SerpRagHybridFormatter.format(root);
        assertThat(out).contains("\"rank\":1");
        assertThat(out).contains("\"snippet\":\"S1\"");
        assertThat(out).contains("\"rank\":20");
        assertThat(out).contains("\"snippet\":\"S20\"");
        assertThat(out).contains("\"rank\":21");
        assertThat(out).doesNotContain("DROP");
    }

    @Test
    void capsAt100Rows() throws Exception {
        var arr = MAPPER.createArrayNode();
        for (int i = 1; i <= 105; i++) {
            var o = MAPPER.createObjectNode();
            o.put("position", i);
            o.put("title", "x");
            o.put("link", "https://x");
            o.put("snippet", "s");
            arr.add(o);
        }
        var root = MAPPER.createObjectNode();
        root.set("organic_results", arr);
        String out = SerpRagHybridFormatter.format(root);
        long lineCount = out.lines().filter(l -> l.startsWith("{")).count();
        assertThat(lineCount).isEqualTo(100);
    }
}
