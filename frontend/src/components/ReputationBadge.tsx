type Props = { band?: string | null; score?: number | null };

/**
 * AI 回答での語られ方（評判）を高／中／低で示すバッジ。
 *
 * Why: 評判は「どれだけ見えているか（SoM）」とは別の軸で、混ぜると数字の意味が濁る（#62）。
 * 一覧では帯で示し、解析全体では平均値を数値で示す。言及の無いクエリには評判が存在しないため何も出さない。
 */
export function ReputationBadge({ band, score }: Props) {
  if (band !== "HIGH" && band !== "MEDIUM" && band !== "LOW") {
    return null;
  }
  const label = band === "HIGH" ? "評判 高" : band === "MEDIUM" ? "評判 中" : "評判 低";
  const tone =
    band === "HIGH"
      ? "bg-emerald-100 text-emerald-800"
      : band === "MEDIUM"
        ? "bg-slate-100 text-slate-600"
        : "bg-rose-100 text-rose-800";
  return (
    <span
      title={score !== null && score !== undefined ? `評判スコア ${score} / 100` : undefined}
      className={`inline-flex shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold ${tone}`}
    >
      {label}
    </span>
  );
}
