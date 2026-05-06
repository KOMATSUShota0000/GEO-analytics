package com.geo.analytics.application.dto;

public record SmartCrawlPage(String url, CrawledPageData crawled, int ordinal) {}
