// ルーブリックLLM10項目の人間可読ラベル（criterionId→日本語）。バックエンドのenum名に対応。
// Why: スコア内訳と改善タスクの根拠欄で同じ項目名を見せるため1か所に置く。
//      改善タスク生成プロンプト（RemediationTaskPrompts#criterionLabel）も同じ名前を使う。
export const CRITERION_LABELS: Record<string, string> = {
  DIRECT_ANSWER_FIRST: "結論ファースト構成",
  ATOMIC_FACTS: "数値化された実績データ",
  SOLUTION_SCENARIOS: "導入事例・活用シーン",
  VERIFIABLE_AUTHORITY: "証明できる専門性",
  FAQ_PRESENCE: "FAQ（よくある質問）の記述",
  NUMBERED_PROCESS_FLOW: "番号付きの詳細な手順フロー",
  ENTITY_BIOGRAPHY: "具体的な経歴・バイオグラフィー",
  LOCAL_CONTEXT: "地域特有のコンテキスト",
  PRICE_AND_CONSTRAINTS: "詳細な料金体系と制約",
  EXTERNAL_CITATIONS: "外部ソースへの言及",
};

export function criterionLabel(criterionId: string): string {
  return CRITERION_LABELS[criterionId] ?? criterionId;
}
