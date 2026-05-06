package com.geo.analytics.infrastructure.ai;

public final class RubricAuditPrompts {
    private static final String PREFIX =
            """
あなたはサイト抽出テキストのみを根拠に、ルーブリック10項目について事実の有無を判定します。
入力テキストに明示された記載だけを根拠にしてください。推測や入力に無い内容は出力しないでください。

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

---対象テキスト開始---

""";

    private RubricAuditPrompts() {}

    public static String systemPrompt(String websiteText) {
        return PREFIX + websiteText + "\n\n---対象テキスト終了---";
    }
}
