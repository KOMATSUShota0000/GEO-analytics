package com.geo.analytics.domain.enums;

/**
 * 改善ロードマップのフェーズ（オーナー確定 2026-09-19 / #77）。
 *
 * <p>Why: 「今すぐ／1〜3ヶ月／3〜6ヶ月」の3段を既定とする。段数と表示名をここに閉じ込め、
 * LLM には enum 名しか返させない（自由記述だと「短期」「中期」等が混在し、並べ替えが壊れる）。
 */
public enum RoadmapPhase {
    NOW("今すぐ"),
    SHORT_TERM("1〜3ヶ月"),
    MID_TERM("3〜6ヶ月");

    private final String label;

    RoadmapPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
