package com.geo.analytics.infrastructure.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CrawledPageData;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JsoupPageExtractor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CHROME_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;

    private JsoupPageExtractor() {
    }

    public static CrawledPageData extract(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        try {
            Document document = Jsoup.connect(url)
                .userAgent(CHROME_USER_AGENT)
                .timeout(CONNECT_TIMEOUT_MILLIS)
                .ignoreHttpErrors(false)
                .get();
            return buildFromDocument(url, document);
        } catch (Exception exception) {
            throw new IllegalStateException("Jsoup extraction failed: " + url, exception);
        }
    }

    public static CrawledPageData buildFromDocument(String canonicalUrl, Document document) {
        Document clone = document.clone();
        clone.select("script, style, noscript").remove();
        String bodyText = clone.body() != null ? clone.body().text() : "";
        String mainContent = PageContentNormalizer.normalizeVisibleText(bodyText);
        Map<String, String> metaTags = extractMetaTags(document);
        String contentHash = DigestUtils.sha256Hex(mainContent.getBytes(StandardCharsets.UTF_8));
        Elements h1Elements = document.select("h1");
        Elements h2Elements = document.select("h2");
        boolean h1Ok = h1Elements.size() >= 1;
        boolean h2Ok = h2Elements.size() >= 1;
        String seoSummary = buildSeoTechnicalEvidenceSummary(document, metaTags, h1Elements.size(), h2Elements.size());
        return new CrawledPageData(canonicalUrl, mainContent, contentHash, seoSummary, metaTags, h1Ok, h2Ok);
    }

    private static String buildSeoTechnicalEvidenceSummary(
            Document document,
            Map<String, String> metaTags,
            int h1Count,
            int h2Count) {
        String schemaPart = summarizeSchemaOrg(document);
        String descPart = summarizeMetaDescription(metaTags.get("description"));
        String headingPart = summarizeHeadings(h1Count, h2Count);
        String robotsPart = summarizeRobots(metaTags.get("robots"));
        return String.join(", ", List.of(schemaPart, descPart, headingPart, robotsPart));
    }

    private static String summarizeSchemaOrg(Document document) {
        List<String> scripts = extractLdJsonStrings(document);
        if (scripts.isEmpty()) {
            return "Schema.org: 未実装(GEO評価に致命的欠陥)";
        }
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String raw : scripts) {
            collectSchemaTypes(raw, types);
        }
        if (types.isEmpty()) {
            return "Schema.org: 実装あり(型抽出不可)";
        }
        return "Schema.org: 実装あり(" + String.join(", ", types) + ")";
    }

    private static void collectSchemaTypes(String rawJson, Set<String> sink) {
        if (rawJson == null || rawJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            collectTypesRecursive(root, sink);
        } catch (Exception ignored) {
            sink.add("パース不能フラグメント");
        }
    }

    private static void collectTypesRecursive(JsonNode node, Set<String> sink) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode t = node.get("@type");
            if (t != null) {
                if (t.isTextual()) {
                    String v = t.asText().strip();
                    if (!v.isEmpty()) {
                        sink.add(v);
                    }
                } else if (t.isArray()) {
                    for (JsonNode x : t) {
                        if (x != null && x.isTextual()) {
                            String v = x.asText().strip();
                            if (!v.isEmpty()) {
                                sink.add(v);
                            }
                        }
                    }
                }
            }
            JsonNode graph = node.get("@graph");
            if (graph != null && graph.isArray()) {
                for (JsonNode g : graph) {
                    collectTypesRecursive(g, sink);
                }
            }
            var it = node.fields();
            while (it.hasNext()) {
                var e = it.next();
                collectTypesRecursive(e.getValue(), sink);
            }
        } else if (node.isArray()) {
            for (JsonNode c : node) {
                collectTypesRecursive(c, sink);
            }
        }
    }

    private static String summarizeMetaDescription(String description) {
        if (description == null || description.isBlank()) {
            return "Description: 欠落";
        }
        String d = description.strip();
        int len = d.codePointCount(0, d.length());
        if (len < 30) {
            return "Description: 短すぎ";
        }
        if (len > 200) {
            return "Description: 冗長";
        }
        return "Description: 適切";
    }

    private static String summarizeHeadings(int h1Count, int h2Count) {
        String h1Label;
        if (h1Count <= 0) {
            h1Label = "H1欠落";
        } else if (h1Count == 1) {
            h1Label = "H1単独";
        } else {
            h1Label = "H1重複あり";
        }
        String h2Label = h2Count >= 1 ? "H2あり" : "H2欠落";
        return "Heading: " + h1Label + ", " + h2Label;
    }

    private static String summarizeRobots(String robots) {
        if (robots == null || robots.isBlank()) {
            return "Robots: メタなし(既定に依存)";
        }
        String r = robots.strip().toLowerCase();
        if (r.contains("noindex")) {
            return "Robots: noindex検出";
        }
        return "Robots: 記載あり";
    }

    private static List<String> extractLdJsonStrings(Document document) {
        Elements scripts = document.select("script[type=application/ld+json]");
        List<String> result = new ArrayList<>();
        for (Element scriptElement : scripts) {
            String data = scriptElement.data();
            if (data != null && !data.isBlank()) {
                result.add(data.strip());
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, String> extractMetaTags(Document document) {
        Map<String, String> map = new LinkedHashMap<>();
        putMetaProperty(document, map, "og:title");
        putMetaProperty(document, map, "og:description");
        putMetaProperty(document, map, "og:url");
        putMetaProperty(document, map, "og:type");
        putMetaName(document, map, "description");
        putMetaName(document, map, "keywords");
        putMetaName(document, map, "robots");
        putMetaName(document, map, "viewport");
        return Map.copyOf(map);
    }

    private static void putMetaProperty(Document document, Map<String, String> targetMap, String propertyValue) {
        Element element = document.selectFirst("meta[property=\"" + propertyValue + "\"]");
        if (element != null) {
            String content = element.attr("content");
            if (content != null && !content.isBlank()) {
                targetMap.put(propertyValue, content.strip());
            }
        }
    }

    private static void putMetaName(Document document, Map<String, String> targetMap, String nameValue) {
        Element element = document.selectFirst("meta[name=\"" + nameValue + "\"]");
        if (element != null) {
            String content = element.attr("content");
            if (content != null && !content.isBlank()) {
                targetMap.put(nameValue, content.strip());
            }
        }
    }
}
