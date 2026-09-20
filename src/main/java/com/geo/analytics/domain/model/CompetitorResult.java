package com.geo.analytics.domain.model;

/**
 * AI 回答に同時に登場した他ブランドの測定結果。
 *
 * <p>Why: 保存（{@code job_competitor_scores}）と集計（{@link com.geo.analytics.domain.service.CompetitorShareAggregator}）
 * が使うためドメインに置く（#112）。旧実装は6項目のうち実体があるのは名前だけで、シェアは LLM の自己申告、引用順位は配列の添字、
 * 可視性ステージは自社の値のコピー、照合状態は固定値、言及回数は 0 ハードコードだった（#64）。
 * オーナー確定（2026-09-19）により、**回答文から Java で実測できる値だけを持つ**。作れない項目は持たない。
 *
 * @param competitorLabel   名寄せ後の表記（#65）
 * @param somScore          回答文中の言及から算出した SoM。自社と同じ式で出す
 * @param aiCitationPosition 回答文での登場順（#66 と同じ規則）。登場しなければ null
 * @param mentionCount      回答文中の言及回数（Java 実測）
 */
public record CompetitorResult(
        String competitorLabel,
        Double somScore,
        Integer aiCitationPosition,
        int mentionCount) {}
