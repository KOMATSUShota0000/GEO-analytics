package com.geo.analytics.infrastructure.crawler;

import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.port.WebCrawlerPort;

/**
 * SSR しない HTML を {@link JsoupPageExtractor} で取得する軽量クローラ実装。
 */
public final class JsoupWebCrawlerAdapter implements WebCrawlerPort {

    @Override
    public CrawledPageData extractContent(String url) {
        return JsoupPageExtractor.extract(url);
    }
}
