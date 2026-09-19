package com.geo.analytics.application.dto;

/**
 * AI Overview の測定結果。
 *
 * <p>Why: 生JSONだけを返していたため、せっかく取得した AI 回答本文が検証へ渡らず捨てられていた（ADR-039）。
 * 本文を同じ結果に持たせ、そのクエリの検証材料として使う（#92）。
 */
public record SgeMentionResult(boolean mentioned, int mentionCount, String rawResponseJson, String bodyText) {
    public SgeMentionResult {
        bodyText = bodyText != null ? bodyText : "";
    }
}
