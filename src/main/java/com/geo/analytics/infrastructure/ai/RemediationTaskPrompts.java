package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.domain.enums.RubricCriterionId;
import java.util.List;

public final class RemediationTaskPrompts {

    /**
     * Why: 旧プロンプトには読み手と書き方の指示が無く、「LLM」「権威性の担保」などの用語や、入力の
     * {@code criterionId=... self_status=...} をそのまま根拠欄へ写した文章が出ていた（#139）。
     * 画面の文章は代理店の提案書にそのまま貼られ、クライアントはエンジニアとは限らないため、読み手を明示する。
     */
    private static final String SYSTEM_PREFIX =
            """
あなたは GEO（ChatGPT や Gemini などの生成AIの回答に、自社が取り上げられるようにする施策）のコンサルタントです。
以下の「ギャップ項目」は、自社サイトでは満たせていない（または一部しか満たせていない）一方で、競合サイトのいずれかは満たしている診断項目です。項目ごとに改善タスクを提案してください。

読み手:
- Web制作会社・代理店の担当者と、その先のクライアント（経営者・広報・店舗の担当者など）です。エンジニアとは限りません。
- 出力した文章は、提案書や見積りにそのまま貼り付けられます。

書き方のルール:
- 平易な日本語の「です・ます」調で、1文を短く書くこと。
- 「LLM」とは書かず「AI」または「生成AI（ChatGPT など）」と書くこと。「GEO露出」「権威性の担保」「エンティティ」「ルーブリック」などの業界用語・内部用語は使わないこと。
- 作業に欠かせない専門用語（例: 構造化データ、HTML の見出しタグ）は使ってよい。その場合は最初に出てくる箇所で、かっこ書きで一言説明を添えること（例: 構造化データ（AIや検索エンジンが読み取りやすい形式で書いた情報））。
- 入力の見出し語や英語の判定値（YES / NO / PARTIAL など）をそのまま出力に含めないこと。項目は「診断項目名」で呼ぶこと。
- 同じ内容を title・content・rationale・evidence で繰り返さないこと。

タスクの種類（category）:
- SPIKE（すぐ直せる）: 既存ページの文章を書き足す・書き直すなど、数時間で終わる作業。
- SLAB（時間がかかる）: 新しいページの制作、社内の運用体制づくり、サイトの仕組みの改修など、計画を立てて進める作業。
- 各ギャップ項目について、SPIKE と SLAB を必ず1件ずつ提案すること。

各フィールドの書き方:
- title: 何をするかが一目でわかる「〜する」の形で、30文字以内（例: 各サービスページに料金の目安を書く）。
- content: 実行手順だけを Markdown の番号付きリストで書くこと。見出しや「なぜ必要か」の説明は書かない（理由は rationale に書く）。各手順は1〜2文。
- rationale: このタスクで AI の回答に取り上げられやすくなる理由を1〜2文で書くこと。
- evidence: 何を根拠にした提案かを、診断項目名と、自社・競合の状況で1〜2文にまとめること。サイトの記載を引用する場合は「」で短く引用すること。入力に無い内容を書いてはならない。
  （例: 診断項目「詳細な料金体系と制約」：自社サイトには料金の記載がありません。競合サイトには「初期費用 5万円〜」と書かれています。）
- 推測や入力に無い情報を新たに追加してはならない。入力の範囲内で提案すること。
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
     *
     * <p>項目IDや判定値を英語のまま渡すと、AI がそれを根拠欄へそのまま写す（#139）。
     * 画面と同じ日本語の項目名と状態で渡し、出力もその言葉で書かせる。
     */
    public static String userPayload(List<GapContext> gaps) {
        StringBuilder sb = new StringBuilder(gaps.size() * 256 + 64);
        sb.append("--- ギャップ項目開始 ---\n");
        for (int i = 0; i < gaps.size(); i++) {
            GapContext g = gaps.get(i);
            sb.append("[")
                    .append(i + 1)
                    .append("] 診断項目名: ")
                    .append(criterionLabel(g.criterionId()));
            sb.append("\n    自社サイトの状態: ")
                    .append(selfStatusLabel(g.selfStatus()));
            sb.append("\n    自社サイトの該当箇所: ")
                    .append(quoteOrNone(g.selfEvidence()));
            sb.append("\n    競合サイトの該当箇所: ")
                    .append(quoteOrNone(g.competitorEvidence()));
            sb.append("\n");
        }
        sb.append("--- ギャップ項目終了 ---\n");
        return sb.toString();
    }

    /** Why: 画面のスコア内訳（GeoScoreBreakdown）と同じ名前にし、根拠欄と内訳で項目名がずれないようにする。 */
    static String criterionLabel(String criterionId) {
        if (criterionId == null) {
            return "";
        }
        RubricCriterionId id;
        try {
            id = RubricCriterionId.valueOf(criterionId);
        } catch (IllegalArgumentException illegalArgumentException) {
            return criterionId;
        }
        return switch (id) {
            case DIRECT_ANSWER_FIRST -> "結論ファースト構成";
            case ATOMIC_FACTS -> "数値化された実績データ";
            case SOLUTION_SCENARIOS -> "導入事例・活用シーン";
            case VERIFIABLE_AUTHORITY -> "証明できる専門性";
            case FAQ_PRESENCE -> "FAQ（よくある質問）の記述";
            case NUMBERED_PROCESS_FLOW -> "番号付きの詳細な手順フロー";
            case ENTITY_BIOGRAPHY -> "具体的な経歴・バイオグラフィー";
            case LOCAL_CONTEXT -> "地域特有のコンテキスト";
            case PRICE_AND_CONSTRAINTS -> "詳細な料金体系と制約";
            case EXTERNAL_CITATIONS -> "外部ソースへの言及";
            default -> criterionId;
        };
    }

    private static String selfStatusLabel(String selfStatus) {
        if ("NO".equals(selfStatus)) {
            return "記載なし";
        }
        if ("PARTIAL".equals(selfStatus)) {
            return "一部のみ記載あり";
        }
        return "不明";
    }

    private static String quoteOrNone(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "（なし）";
        }
        return "「" + evidence.strip() + "」";
    }

    public record GapContext(
            String criterionId, String selfStatus, String selfEvidence, String competitorEvidence) {}
}
