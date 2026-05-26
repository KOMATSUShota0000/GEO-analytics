package com.geo.analytics.infrastructure.crawler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.port.WebCrawlerPort;
import org.apache.commons.codec.digest.DigestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class CachedWebCrawlerDecorator implements WebCrawlerPort {
    private static final String CACHE_KEY_PREFIX = "geo:crawl:v1:";
    private final WebCrawlerPort delegateWebCrawlerPort;
    private final ObjectMapper objectMapper;
    private final TieredCrawlCache tieredCrawlCache;
    private final long cacheTtlSeconds;

    public CachedWebCrawlerDecorator(
            WebCrawlerPort delegateWebCrawlerPort,
            ObjectMapper objectMapper,
            TieredCrawlCache tieredCrawlCache,
            long cacheTtlSeconds) {
        this.delegateWebCrawlerPort = delegateWebCrawlerPort;
        this.objectMapper = objectMapper;
        this.tieredCrawlCache = tieredCrawlCache;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @Override
    public CrawledPageData extractContent(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        String normalizedUrl = url.trim();
        String cacheKey = CACHE_KEY_PREFIX + DigestUtils.sha256Hex(normalizedUrl.getBytes(StandardCharsets.UTF_8));
        return tieredCrawlCache.get(cacheKey)
            .flatMap(json -> deserializeSafely(json, cacheKey))
            .orElseGet(() -> fetchAndCache(normalizedUrl, cacheKey));
    }

    private Optional<CrawledPageData> deserializeSafely(String jsonPayload, String cacheKey) {
        try {
            return Optional.of(objectMapper.readValue(jsonPayload, CrawledPageData.class));
        } catch (JsonProcessingException jsonProcessingException) {
            tieredCrawlCache.invalidate(cacheKey);
            return Optional.empty();
        }
    }

    private CrawledPageData fetchAndCache(String normalizedUrl, String cacheKey) {
        CrawledPageData crawledPageData = delegateWebCrawlerPort.extractContent(normalizedUrl);
        try {
            String serialized = objectMapper.writeValueAsString(crawledPageData);
            tieredCrawlCache.put(cacheKey, serialized, cacheTtlSeconds);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalStateException(jsonProcessingException);
        }
        return crawledPageData;
    }
}
