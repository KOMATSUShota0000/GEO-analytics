package com.geo.analytics.application.dto;

import java.util.List;

public record DomainDeepAuditContext(
        List<SmartCrawlPage> pages,
        String mergedAuditText,
        String canonicalEntryUrl) {
    // 結合監査テキストの総文字上限。巡回ページを増やした分、主要サブページの本文も収まるよう
    // 15k→24k へ拡張（rubric評価は1回のLLM呼び出しで、入力増分は概ね1円未満/解析と試算）。
    public static final int MERGED_TOTAL_MAX_CHARS = 24_000;

    public SmartCrawlPage primaryPage() {
        return pages.getFirst();
    }
}
