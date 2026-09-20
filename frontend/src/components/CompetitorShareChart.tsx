import { useLayoutEffect, useState } from "react";
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { CompetitorShare } from "../types/analysis";

/** Why: 自社はブランドカラー（ホワイトラベル）、競合は彩度を落とした灰系で段階を付ける（核②）。 */
const COMPETITOR_COLORS = ["#64748b", "#94a3b8", "#b4c0cf", "#cbd5e1", "#e2e8f0", "#f1f5f9"];

function useCssVarColor(variable: string, fallback: string): string {
  const [color, setColor] = useState(fallback);
  useLayoutEffect(() => {
    const raw = getComputedStyle(document.documentElement).getPropertyValue(variable).trim();
    if (raw.length > 0) {
      setColor(raw);
    }
  }, [variable]);
  return color;
}

/**
 * 競合シェア円グラフ（#112）。
 *
 * <p>オーナー確定（2026-09-20）: 解析全体で1枚、分母は SoM スコア比。
 * クエリごとの内訳は出さない（提案書に貼る図を1枚に確定させるため）。
 */
export function CompetitorShareChart({
  shares,
  isPdfMode = false,
}: {
  shares: CompetitorShare[];
  isPdfMode?: boolean;
}): JSX.Element | null {
  const brandColor = useCssVarColor("--brand-color", "#4f46e5");
  if (shares.length === 0) {
    return null;
  }
  const data = shares.map((s) => ({ name: s.label, value: s.share, self: s.self }));
  return (
    <section
      className="pdf-avoid-break mb-6 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
      style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
    >
      <h2 className="text-sm font-semibold text-slate-900">AI回答内のシェア（自社 vs 競合）</h2>
      <p className="mt-1 text-xs leading-relaxed text-slate-600">
        この解析の全クエリを合算した SoM スコア比です。AI回答の中でどれだけの割合を占めているかを示します。
      </p>
      <div className="mt-3" style={{ width: "100%", height: 280 }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              cx="50%"
              cy="50%"
              outerRadius={92}
              isAnimationActive={!isPdfMode}
              label={(entry: { name?: string; value?: number }) =>
                `${entry.name ?? ""} ${(entry.value ?? 0).toFixed(1)}%`
              }
              labelLine={false}
            >
              {data.map((entry, index) => (
                <Cell
                  key={`slice-${entry.name}`}
                  fill={entry.self ? brandColor : COMPETITOR_COLORS[index % COMPETITOR_COLORS.length]}
                />
              ))}
            </Pie>
            <Tooltip formatter={(value: number | string) => `${Number(value).toFixed(1)}%`} />
            <Legend verticalAlign="bottom" height={28} />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}
