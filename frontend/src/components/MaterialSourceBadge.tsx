type Props = { materialSource?: string | null };

/**
 * 各クエリの評価が「実測の AI Overview 本文」と「AI Overview が出なかったための推定」のどちらを
 * 材料にしたかを示すバッジ。
 *
 * Why: 同じスコアでも根拠の強さが違う。どちらを見ているのかが分からないまま提案に使うと、
 * 実測でない数字まで実測として伝わってしまう（ADR-039 / ADR-045）。
 */
export function MaterialSourceBadge({ materialSource }: Props) {
  if (materialSource !== "MEASURED" && materialSource !== "ESTIMATED") {
    return null;
  }
  const measured = materialSource === "MEASURED";
  return (
    <span
      title={
        measured
          ? "Google AI Overview の実測本文を材料に評価しました"
          : "AI Overview が表示されなかったため、生成AIの回答を推定して評価しました"
      }
      className={
        measured
          ? "inline-flex shrink-0 rounded-full bg-sky-100 px-2 py-0.5 text-[11px] font-semibold text-sky-800"
          : "inline-flex shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600"
      }
    >
      {measured ? "実測" : "推定"}
    </span>
  );
}
