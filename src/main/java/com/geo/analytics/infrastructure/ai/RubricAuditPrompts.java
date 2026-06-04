package com.geo.analytics.infrastructure.ai;

public final class RubricAuditPrompts {
    private static final String PREFIX =
            """
あなたは対象テキスト（サイトから抽出した本文）および、付与される場合があるジョブ文脈（ユーザー由来の事業背景・依頼など）の双方を材料に、ルーブリック10項目について事実の有無を判定します。
ジョブ文脈ブロックが無い場合は対象テキストのみを根拠にします。いずれの場合も入力に明示された記載だけを根拠にし、推測や入力に無い内容は出力しないでください。

各項目について status は YES / PARTIAL / NO のいずれかです。
部分的な記載のみが入力にある場合は PARTIAL、記載がなく判定できない場合は NO とします。
evidence は入力テキストからの連続した直接引用のみです。該当が無い場合は空文字にしてください。

criterionId は次の識別子のみを使用し、items にちょうど10要素を出力してください。
各 criterionId は1回ずつ必ず含め、欠落・重複は禁止です。

DIRECT_ANSWER_FIRST — 冒頭の直接的回答
ATOMIC_FACTS — 数値化された実績データ
SOLUTION_SCENARIOS — 具体的な解決シナリオ
VERIFIABLE_AUTHORITY — 検証可能な権威性・資格
FAQ_PRESENCE — FAQ の記述
NUMBERED_PROCESS_FLOW — 番号付きの詳細フロー
ENTITY_BIOGRAPHY — 具体的経歴とバイオグラフィー
LOCAL_CONTEXT — 地域特有のコンテキスト
PRICE_AND_CONSTRAINTS — 詳細な料金体系と制約
EXTERNAL_CITATIONS — 外部ソースへの言及

応答は指定されたJSON構造に厳密に従ってください。

""";

    private RubricAuditPrompts() {}

    /** ルーブリック判定の指示部。Gemini には systemInstruction として渡す。 */
    public static String systemInstruction() {
        return PREFIX;
    }

    /**
     * 判定材料（ジョブ文脈＋対象テキスト）。Gemini の contents（UserMessage）として渡す。
     * SystemMessage だけで chat を呼ぶと Gemini が "contents is not specified"(400) を返すため、
     * 材料は必ず UserMessage 側へ載せる。
     */
    public static String userPayload(String websiteText, String jobContextBlock) {
        StringBuilder sb = new StringBuilder();
        if (jobContextBlock != null && !jobContextBlock.isBlank()) {
            sb.append("---ジョブ文脈---\n\n");
            sb.append(jobContextBlock.strip());
            sb.append("\n\n");
        }
        sb.append("---対象テキスト開始---\n\n");
        sb.append(websiteText);
        sb.append("\n\n---対象テキスト終了---");
        return sb.toString();
    }
}
