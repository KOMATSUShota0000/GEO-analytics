import { useMemo } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
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
    return 0.0;
  }
  const min = Math.min(...finite);
  return Math.max(0.0, min - 5.0);
}

// 採点ロジック版を短縮表記にする（V13_GEO4AXIS→V13 等）。凡例・注記の誤読防止用。
function shortVersion(v: string | null): string | null {
  if (v === null || v.length === 0) {
    return null;
  }
  if (v.startsWith("V13")) {
    return "V13";
  }
  if (v.startsWith("V12")) {
    return "V12";
  }
  return v;
}

interface VersionBoundary {
  date: string;
  label: string;
}

// 隣接スナップショットで calculationVersion が変化した点を切替境界として抽出する。
// 両端が既知（非null）かつ異なるときのみ。null（版不明の旧データ）は境界に含めない。
function findVersionBoundaries(data: AssetSnapshotChartPoint[]): VersionBoundary[] {
  const out: VersionBoundary[] = [];
  for (let i = 1; i < data.length; i++) {
    const prev = data[i - 1].calculationVersion;
    const cur = data[i].calculationVersion;
    if (prev !== null && cur !== null && prev !== cur) {
      const label = shortVersion(cur);
      out.push({ date: data[i].snapshotDate, label: label ?? cur });
    }
  }
  return out;
}

export interface GrowthTrajectoryChartProps {
  data: AssetSnapshotChartPoint[];
  isPdfMode: boolean;
}

export function GrowthTrajectoryChart({
  data,
  isPdfMode,
}: GrowthTrajectoryChartProps): JSX.Element | null {
  const yMin = useMemo(
    () => computeReadinessYMin(data.map((d) => d.geoReadinessScore)),
    [data],
  );
  const versionBoundaries = useMemo(() => findVersionBoundaries(data), [data]);
  if (data.length === 0) {
    return null;
  }

  return (
    <section
      className="strategy-chart-shell pdf-avoid-break mb-6 rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
      style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
    >
      <h2 className="text-sm font-semibold text-slate-900">成長の推移</h2>
      <p className="mt-1 text-xs leading-relaxed text-slate-600">
        GEO Readiness Score の移り変わりです（この解析の日までの90日間）。同じ屋号・ブランド名で解析するたびに、点が1つ増えます。
      </p>
      {versionBoundaries.length > 0 ? (
        <p className="mt-2 text-xs font-light leading-relaxed text-slate-500">
          縦の点線は採点ロジックの切替点です。線の前後でスコアの算出基準が異なるため、線をまたぐ増減はそのまま比較できません。
        </p>
      ) : null}
      <div className="mt-3 h-[280px] w-full min-w-0">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{top:8,right:8,left:0,bottom:0}}>
            <CartesianGrid strokeDasharray="3 6" stroke="rgba(148,163,184,0.35)" vertical={false} />
            <XAxis
              dataKey="snapshotDate"
              tick={{fontSize:11,fill:"#64748b"}}
              tickFormatter={(v: string) => formatAuditDate(v)}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              domain={[yMin,100.0]}
              width={40}
              tick={{fontSize:11,fill:"#64748b"}}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip
              contentStyle={{
                borderRadius:"12px",
                border:"1px solid rgba(226,232,240,0.9)",
                boxShadow:"0 10px 40px rgba(15,23,42,0.08)",
              }}
              labelFormatter={(v: string) => formatAuditDate(String(v))}
            />
            <Legend wrapperStyle={{fontSize:"12px",paddingTop:"12px"}} />
            {versionBoundaries.map((b) => (
              <ReferenceLine
                key={b.date}
                x={b.date}
                stroke="#94a3b8"
                strokeDasharray="4 4"
                label={{
                  value: `${b.label} へ`,
                  position: "insideTopRight",
                  fontSize: 10,
                  fill: "#64748b",
                }}
              />
            ))}
            <Line
              type="monotone"
              dataKey="geoReadinessScore"
              name="GEO Readiness"
              stroke="var(--brand-color)"
              strokeWidth={2}
              dot={{r:isPdfMode?3:4}}
              activeDot={{r:isPdfMode?4:5}}
              isAnimationActive={!isPdfMode}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
      {data.length === 1 ? (
        <p className="mt-2 text-xs leading-relaxed text-slate-500">
          いまは点が1つだけです。同じ屋号・ブランド名でもう一度解析すると、線でつながります。
        </p>
      ) : null}
    </section>
  );
}
