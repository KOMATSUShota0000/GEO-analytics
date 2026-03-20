package com.geo.analytics.application.port;

import com.geo.analytics.application.dto.CrawledPageData;

public interface WebCrawlerPort {
    CrawledPageData extractContent(String url);
}
