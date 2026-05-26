import type { ReactNode } from "react";
import type { BenchmarkRow } from "../../hooks/useRelativeBenchmark";

export interface RelativeEvaluationSectionProps {
  rows: BenchmarkRow[];
  locked: boolean;
  available: boolean;
  loading: boolean;
  error: string | null;
}

function SectionShell({ children }: { children: ReactNode }): JSX.Element {
  return (
    <section className="pdf-avoid-break mb-10">
      <div className="mb-6 border-b border-slate-200/80 pb-3">
        <h2 className="text-lg font-semibold tracking-tight text-slate-900">相対評価（ベンチマーク）</h2>
        <p className="mt-0.5 text-xs font-light text-slate-500">自社と競合のGEO指標比較</p>
      </div>
      {children}
    </section>
  );
}

export function RelativeEvaluationSection({
  rows,
  locked,
  available,
  loading,
  error,
}: RelativeEvaluationSectionProps): JSX.Element {
  if (loading) {
    return (
      <SectionShell>
        <div className="h-32 animate-pulse rounded-xl border border-slate-200 bg-slate-100/70" />
      </SectionShell>
    );
  }

  if (error != null) {
    return (
      <SectionShell>
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
          競合比較データの取得に失敗しました：{error}
        </div>
      </SectionShell>
    );
  }

  if (locked) {
    return (
      <SectionShell>
        <div className="relative overflow-hidden rounded-xl border border-sky-100 bg-sky-50/70 px-6 py-8 text-center">
          <p className="select-none text-sm font-semibold text-sky-900">
            🔒 競合ベンチマークは Pro プラン以上で開示されます
          </p>
          <p className="mt-1 text-xs text-sky-700">
            自社と競合のAI言及シェア・構造化シグナル密度・エンティティ解像度を比較できます。
          </p>
        </div>
      </SectionShell>
    );
  }

  if (!available || rows.length === 0) {
    return (
      <SectionShell>
        <div className="rounded-xl border border-slate-200 bg-slate-50/80 px-6 py-8 text-center text-sm text-slate-600">
          解析完了後に競合比較が表示されます。
        </div>
      </SectionShell>
    );
  }

  return (
    <SectionShell>
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
            {rows.map((row) => (
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
                <td className={`px-4 py-3 tabular-nums ${row.gap ? "text-red-950" : ""}`}>
                  {row.competitorLabel}
                </td>
                <td className={`px-4 py-3 font-semibold ${row.gap ? "text-red-700" : "text-emerald-700"}`}>
                  {row.gap ? "要注意" : "拮抗"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </SectionShell>
  );
}
