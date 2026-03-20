package com.geo.analytics.application.dto;

public record CrawledPageData(
    String url,
    String content,
    String contentHash
) {}