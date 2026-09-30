package com.geo.analytics.domain.enums;

/**
 * 議論の発言が根拠にした材料の種類（#196）。
 *
 * <p>Why: 議論の材料の節（{@code DebateMaterialFormatter} の「■」の見出し）と改善タスクの一覧に対応させる。
 * 画面では「根拠：測定結果（質問ごとの言及）」「根拠：改善タスク 2」のように見せる。
 */
public enum DebateEvidenceKind {
    QUERY_MENTIONS("AIへの質問と、回答に社名が出たか"),
    COMPETITORS("回答によく出た他社"),
    SITE_DIAGNOSIS("サイト診断で足りなかった点"),
    REMEDIATION_TASK("改善タスク");

    private final String label;

    DebateEvidenceKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
