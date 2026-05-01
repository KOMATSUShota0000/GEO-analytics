package com.geo.analytics.application.exception;

/**
 * クエリ提案オーケストレーションのどの段階で失敗したかを区別する。
 */
public enum QueryProposalPhase {
    SCRAPING,
    AI_ANALYSIS,
    VALIDATION
}
