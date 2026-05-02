import { TrendingUp } from "lucide-react";
import { useMemo } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatAuditDate } from "../../types/analysis";
import type { AssetSnapshotChartPoint } from "../../hooks/useProjectAssetSnapshots";

function computeReadinessYMin(values: number[]): number {
  const finite = values.filter((n) => Number.isFinite(n));
  if (finite.length === 0) {
    return 0;
  }
  const minScore = Math.min(...finite);
  const padded = Math.floor(minScore / 5) * 5 - 5;
  return Math.max(0, padded);
}

function ChartPlaceholder(): JSX.Element {
  return (
    <div className="flex min-h-[260px] flex-col items-center justify-center rounded-xl border border-dashed border-slate-200/90 bg-gradient-to-b from-slate-50/40 to-white px-6 text-center">
      <p className="max-w-sm text-sm font-light tracking-wide text-slate-500">データ蓄積中…</p>
    </div>
  );
}

export interface GrowthTrajectoryChartProps {
  data: AssetSnapshotChartPoint[];
  brandColor: string;
  isPdfMode: boolean;
}

export function GrowthTrajectoryChart({
  data,
  brandColor,
  isPdfMode,
}: GrowthTrajectoryChartProps): JSX.Element {
  const anim = !isPdfMode;
  const yMin = useMemo(
    () => computeReadinessYMin(data.map((d) => d.geoReadinessScore)),
    [data],
  );
  const ok = data.length >= 1;

  return (
    <div className="strategy-chart-shell pdf-avoid-break rounded-2xl border border-slate-200/80 bg-gradient-to-br from-white via-slate-50/40 to-white p-5 shadow-[0_8px_30px_rgba(15,23,42,0.06)]">
      <div className="mb-4 flex items-center gap-2.5">
        <span
          className="flex h-9 w-9 items-center justify-center rounded-lg shadow-sm ring-1 ring-slate-200/60"
          style={{ color: brandColor, backgroundColor: "rgba(255,255,255,0.9)" }}
        >
          <TrendingUp className="h-4 w-4" strokeWidth={1.75} aria-hidden />
        </span>
        <h3 className="text-sm font-semibold tracking-tight text-slate-800">GEO Readiness の成長軌跡</h3>
      </div>
      {ok ? (
        <div className="h-[280px] w-full min-w-0">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 6" stroke="rgba(148,163,184,0.35)" vertical={false} />
              <XAxis
                dataKey="snapshotDate"
                tick={{ fontSize: 11, fill: "#64748b" }}
                tickFormatter={(v: string) => formatAuditDate(v)}
                axisLine={false}
                tickLine={false}
              />
              <YAxis
                domain={[yMin, 100]}
                width={40}
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
                labelFormatter={(v: string) => formatAuditDate(String(v))}
              />
              <Legend wrapperStyle={{ fontSize: "12px", paddingTop: "12px" }} />
              <Line
                type="monotone"
                dataKey="geoReadinessScore"
                name="GEO Readiness"
                stroke={brandColor}
                strokeWidth={2}
                dot={{ r: isPdfMode ? 3 : 4 }}
                activeDot={{ r: isPdfMode ? 4 : 5 }}
                isAnimationActive={anim}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <ChartPlaceholder />
      )}
    </div>
  );
}
