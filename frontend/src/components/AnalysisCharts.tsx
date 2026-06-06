import { TrendingUp } from "lucide-react";
import { useId, useLayoutEffect, useState } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatAuditDate, type TrendData } from "../types/analysis";

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

function ChartPlaceholder(): JSX.Element {
  return (
    <div className="flex min-h-[260px] flex-col items-center justify-center rounded-xl border border-dashed border-slate-200/90 bg-gradient-to-b from-slate-50/40 to-white px-6 text-center">
      <p className="max-w-sm text-sm font-light tracking-wide text-slate-500">データ蓄積中…</p>
    </div>
  );
}

export interface AnalysisChartsProps {
  isPdfMode: boolean;
  trendData: TrendData[];
  brandLabel: string;
}

export function AnalysisCharts({
  isPdfMode,
  trendData,
  brandLabel,
}: AnalysisChartsProps): JSX.Element {
  const anim = !isPdfMode;
  const gid = useId().replace(/:/g, "");
  const brandColor = useCssVarColor("--brand-color", "#4f46e5");
  const comp1 = useCssVarColor("--chart-competitor-1", "#94a3b8");
  const trendOk = trendData.length >= 1;
  return (
    <section className="pdf-avoid-break mb-8">
      <div className="mb-5 flex items-end justify-between gap-4 border-b border-slate-200/80 pb-3">
        <div>
          <h2 className="text-lg font-semibold tracking-tight text-slate-900">インサイトサマリー</h2>
          <p className="mt-0.5 text-xs font-light text-slate-500">{brandLabel}</p>
        </div>
      </div>
      <div className="rounded-2xl border border-slate-200/80 bg-gradient-to-br from-white via-slate-50/40 to-white p-5 shadow-[0_8px_30px_rgba(15,23,42,0.06)]">
        <div className="mb-4 flex items-center gap-2.5">
          <span
            className="flex h-9 w-9 items-center justify-center rounded-lg shadow-sm ring-1 ring-slate-200/60"
            style={{ color: "var(--brand-color, #4f46e5)", backgroundColor: "rgba(255,255,255,0.9)" }}
          >
            <TrendingUp className="h-4 w-4" strokeWidth={1.75} aria-hidden />
          </span>
          <h3 className="text-sm font-semibold tracking-tight text-slate-800">スコア推移</h3>
        </div>
        {trendOk ? (
          <div className="h-[280px] w-full min-w-0">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trendData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id={`${gid}-som`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={brandColor} stopOpacity={0.35} />
                    <stop offset="100%" stopColor={brandColor} stopOpacity={0.02} />
                  </linearGradient>
                  <linearGradient id={`${gid}-ov`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={comp1} stopOpacity={0.28} />
                    <stop offset="100%" stopColor={comp1} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 6" stroke="rgba(148,163,184,0.35)" vertical={false} />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 11, fill: "#64748b" }}
                  tickFormatter={(v: string) => formatAuditDate(v)}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  domain={[0, 100]}
                  width={36}
                  tick={{ fontSize: 11, fill: "#64748b" }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip
                  contentStyle={{
                    borderRadius: "12px",
                    border: "1px solid rgba(226,232,240,0.9)",
                    boxShadow: "0 10px 40px rgba(15,23,42,0.08)",
                  }}
                  labelFormatter={(v: string) => formatAuditDate(v)}
                />
                <Legend wrapperStyle={{ fontSize: "12px", paddingTop: "12px" }} />
                <Area
                  type="monotone"
                  dataKey="somScore"
                  name="SoM"
                  stroke={brandColor}
                  strokeWidth={2}
                  fill={`url(#${gid}-som)`}
                  isAnimationActive={anim}
                />
                <Area
                  type="monotone"
                  dataKey="overallScore"
                  name="Overall"
                  stroke={comp1}
                  strokeWidth={2}
                  fill={`url(#${gid}-ov)`}
                  isAnimationActive={anim}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        ) : (
          <ChartPlaceholder />
        )}
      </div>
    </section>
  );
}
