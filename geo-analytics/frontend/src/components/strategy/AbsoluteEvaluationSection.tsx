import type { AssetSnapshotChartPoint } from "../../hooks/useProjectAssetSnapshots";
import { GrowthTrajectoryChart } from "./GrowthTrajectoryChart";
import { TrustAccumulationChart } from "./TrustAccumulationChart";

export interface AbsoluteEvaluationSectionProps {
  data: AssetSnapshotChartPoint[];
  brandColor: string;
  isPdfMode: boolean;
}

export function AbsoluteEvaluationSection({
  data,
  brandColor,
  isPdfMode,
}: AbsoluteEvaluationSectionProps): JSX.Element {
  return (
    <section className="pdf-avoid-break mb-10">
      <div className="mb-6 border-b border-slate-200/80 pb-3">
        <h2 className="text-lg font-semibold tracking-tight text-slate-900">絶対評価</h2>
        <p className="mt-0.5 text-xs font-light text-slate-500">GEO Readiness と資産蓄積の推移</p>
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <GrowthTrajectoryChart data={data} brandColor={brandColor} isPdfMode={isPdfMode} />
        <TrustAccumulationChart data={data} brandColor={brandColor} isPdfMode={isPdfMode} />
      </div>
    </section>
  );
}
