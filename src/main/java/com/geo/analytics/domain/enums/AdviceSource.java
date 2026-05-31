package com.geo.analytics.domain.enums;

/**
 * ジョブ全体アドバイスの生成元。フロントの「簡易分析モード」バッジ（仕様書 F-3.1）と
 * フォールバック発動率の事後集計（N-4）に用いる。
 */
public enum AdviceSource {
    /** AI 生成（Pro 議論駆動 or Free 単発のいずれも含む正常生成）。 */
    AI,
    /** LLM 障害・パース失敗等でテンプレート4分類にフォールバックした状態。 */
    TEMPLATE_FALLBACK
}
