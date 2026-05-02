type BenchmarkRow = {
  label: string;
  selfLabel: string;
  competitorLabel: string;
  gap: boolean;
};

const MOCK_ROWS: BenchmarkRow[] = [
  {
    label: "AI言及シェア（モック）",
    selfLabel: "42%",
    competitorLabel: "58%",
    gap: true,
  },
  {
    label: "構造化シグナル密度（モック）",
    selfLabel: "良好",
    competitorLabel: "良好",
    gap: false,
  },
  {
    label: "エンティティ解像度（モック）",
    selfLabel: "要強化",
    competitorLabel: "高",
    gap: true,
  },
];

export function RelativeEvaluationSection(): JSX.Element {
  return (
    <section className="pdf-avoid-break mb-10">
      <div className="mb-6 border-b border-slate-200/80 pb-3">
        <h2 className="text-lg font-semibold tracking-tight text-slate-900">相対評価（ベンチマーク）</h2>
        <p className="mt-0.5 text-xs font-light text-slate-500">
          自社と競合の比較データは今後のフェーズで連携予定です。
        </p>
      </div>
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <table className="w-full border-collapse text-left text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50/90">
              <th className="px-4 py-3 font-semibold text-slate-700">指標</th>
              <th className="px-4 py-3 font-semibold text-slate-700">自社</th>
              <th className="px-4 py-3 font-semibold text-slate-700">競合</th>
              <th className="px-4 py-3 font-semibold text-slate-700">ギャップ</th>
            </tr>
          </thead>
          <tbody>
            {MOCK_ROWS.map((row) => (
              <tr
                key={row.label}
                className={
                  row.gap
                    ? "border-b border-red-100 bg-red-50/90 text-red-900 last:border-0"
                    : "border-b border-slate-100 text-slate-800 last:border-0"
                }
              >
                <td className="px-4 py-3 font-medium">{row.label}</td>
                <td className={`px-4 py-3 tabular-nums ${row.gap ? "text-red-950" : ""}`}>{row.selfLabel}</td>
                <td className={`px-4 py-3 tabular-nums ${row.gap ? "text-red-950" : ""}`}>{row.competitorLabel}</td>
                <td className={`px-4 py-3 font-semibold ${row.gap ? "text-red-700" : "text-emerald-700"}`}>
                  {row.gap ? "要注意" : "拮抗"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
