package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.CrawledPageData;
import org.apache.commons.codec.digest.DigestUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsoupPageExtractor {
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
        List<String> schemaOrg = extractLdJsonStrings(document);
        Map<String, String> metaTags = extractMetaTags(document);
        String contentHash = DigestUtils.sha256Hex(mainContent.getBytes(StandardCharsets.UTF_8));
        return new CrawledPageData(canonicalUrl, mainContent, contentHash, schemaOrg, metaTags);
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
