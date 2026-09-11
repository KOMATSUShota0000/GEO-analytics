package com.geo.analytics.infrastructure.ai;

import java.util.List;

public final class RemediationTaskPrompts {

    private static final String SYSTEM_PREFIX =
            """
あなたは GEO（Generative Engine Optimization）コンサルタントです。
以下に列挙された「自社が NO または PARTIAL であり、かつ競合のいずれかが YES である」ルーブリック項目（ギャップ）に対し、Spike-and-Slab 構造で改善タスクを提案してください。

絶対遵守事項:
- 各ギャップ項目について、Spike（即効策：即時反映可能、Markdown 編集 1〜3 時間以内）と Slab（根本策：抜本的改修、リソース投入が必要）の両方を必ず1件ずつ提案すること。
- 出力 tasks 配列には、最低でも SPIKE が1件以上、SLAB が1件以上含まれていること。
- 各タスクの content は Markdown 形式で、なぜ必要か（GEO 露出 / LLM 引用への影響）と、具体的な実行手順を含めること。
- 推測や入力に無い情報を新たに追加してはならない。証拠（evidence）の範囲内で提案すること。
- impactScore は 0.0 から 1.0 の範囲で、ビジネスへの推定インパクトを表すこと。
- priority は S / A / B のいずれかとし、impactScore が 0.7 以上であれば S、0.4 以上 0.7 未満であれば A、それ未満であれば B を選ぶこと。

応答は指定された JSON Schema に厳密に従い、コード以外のメッセージや前置き・後置きを含めてはならない。
""";

    private RemediationTaskPrompts() {}

    /** ルールのみ。ギャップの実データは {@link #userPayload(List)} 側へ置く。 */
    public static String systemInstruction() {
        return SYSTEM_PREFIX;
    }

    /**
     * Why: Gemini は contents（ユーザーターン）が空のリクエストを 400 で拒否する。
     * 旧実装はギャップ本文までシステム指示に押し込み、ユーザーメッセージを持たなかったため
     * 呼び出すと必ず失敗した（この経路は一度も実行されていなかったため露見していなかった）。
     * ルールはシステム、判定対象のデータはユーザーターン、という他プロンプトと同じ構成に揃える。
     */
    public static String userPayload(List<GapContext> gaps) {
        StringBuilder sb = new StringBuilder(gaps.size() * 256 + 64);
        sb.append("--- ギャップ項目開始 ---\n");
        for (int i = 0; i < gaps.size(); i++) {
            GapContext g = gaps.get(i);
            sb.append("[")
                    .append(i + 1)
                    .append("] criterionId=")
                    .append(g.criterionId());
            sb.append("\n    self_status=")
                    .append(g.selfStatus());
            sb.append("\n    self_evidence=")
                    .append(g.selfEvidence() == null ? "" : g.selfEvidence());
            sb.append("\n    competitor_yes_evidence=")
                    .append(g.competitorEvidence() == null ? "" : g.competitorEvidence());
            sb.append("\n");
        }
        sb.append("--- ギャップ項目終了 ---\n");
        return sb.toString();
    }

    public record GapContext(
            String criterionId, String selfStatus, String selfEvidence, String competitorEvidence) {}
}
