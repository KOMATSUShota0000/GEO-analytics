package com.geo.analytics.application.dto;

/**
 * AI が生成した 1 件の質問本文と、その重要性の根拠（意図）。
 * 未入力は空文字として扱う。{@code null} は受け付けず正規化する。
 */
public record SuggestedQuery(String queryText, String intent) {

    public SuggestedQuery {
        queryText = queryText == null ? "" : queryText;
        intent = intent == null ? "" : intent;
    }
}
