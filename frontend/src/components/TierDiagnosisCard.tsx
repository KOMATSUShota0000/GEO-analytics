import { Lightbulb, Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { getSomTierInfo, somScoreProgressRatio, type SomTierInfo } from "../utils/somTierUtils";

const R = 52;
const C = 2 * Math.PI * R;

export type TierDiagnosisCardProps = {
  somScore: number | null;
  isProPlan: boolean;
  skeleton?: boolean;
};

function TierSkeleton(): JSX.Element {
  return (
    <div className="pdf-avoid-break overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="animate-pulse">
        <div className="h-5 w-40 rounded bg-slate-200" />
        <div className="mt-6 flex flex-col items-center gap-4 sm:flex-row sm:items-start sm:gap-8">
          <div className="relative mx-auto h-36 w-36 shrink-0 rounded-full bg-slate-100 sm:mx-0" />
          <div className="min-w-0 flex-1 space-y-3">
            <div className="h-4 w-full max-w-md rounded bg-slate-200" />
            <div className="h-4 w-full max-w-lg rounded bg-slate-200" />
            <div className="h-4 w-2/3 max-w-sm rounded bg-slate-200" />
          </div>
        </div>
      </div>
    </div>
  );
}

function ProgressRing({ score, tier }: { score: number; tier: SomTierInfo }): JSX.Element {
  const ratio = somScoreProgressRatio(score);
  const dash = C * ratio;
  return (
    <div className="relative mx-auto h-36 w-36 shrink-0 sm:mx-0">
      <svg className="h-full w-full -rotate-90" viewBox="0 0 120 120" aria-hidden>
        <circle
          className="text-slate-200"
          cx="60"
          cy="60"
          r={R}
          fill="none"
          stroke="currentColor"
          strokeWidth="10"
        />
        <circle
          className={tier.ringClass}
          cx="60"
          cy="60"
          r={R}
          fill="none"
          stroke="currentColor"
          strokeWidth="10"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${C}`}
        />
      </svg>
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-2xl font-bold tabular-nums text-slate-900">{score.toFixed(1)}</span>
        <span className="text-[10px] font-medium uppercase tracking-wider text-slate-500">SoM</span>
      </div>
    </div>
  );
}

export function TierDiagnosisCard({ somScore, isProPlan, skeleton }: TierDiagnosisCardProps): JSX.Element {
  const navigate = useNavigate();
  if (skeleton) {
    return <TierSkeleton />;
  }
  if (somScore === null) {
    return <></>;
  }
  const tier = getSomTierInfo(somScore);
  const showUpsell = !isProPlan && somScore >= 69.5;
  return (
    <div className="pdf-avoid-break overflow-hidden rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex flex-col gap-1 border-b border-slate-200 pb-4">
        <div className="flex flex-wrap items-center gap-2">
          <Sparkles className="h-5 w-5 shrink-0 text-amber-600" aria-hidden />
          <h2 className="text-lg font-semibold tracking-tight text-slate-900">市場ポジション診断（Tier）</h2>
        </div>
        <p className="text-xs text-slate-600">バックエンド算出のSoMスコア（0〜100）に基づくGEO戦略の目安です。</p>
      </div>
      <div className="mt-6 flex flex-col items-stretch gap-6 sm:flex-row sm:items-center">
        <ProgressRing score={somScore} tier={tier} />
        <div className="min-w-0 flex-1">
          <div className={`inline-flex max-w-full flex-wrap items-center gap-2 rounded-xl border px-4 py-2 ${tier.accentClass}`}>
            <span className="text-lg font-bold">{tier.title}</span>
            <span className="text-sm font-medium opacity-90">（{tier.subtitle}）</span>
          </div>
          <div className="mt-5 flex gap-3 rounded-xl border border-slate-200 bg-slate-50/90 p-4">
            <Lightbulb className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" aria-hidden />
            <p className="text-sm leading-relaxed text-slate-800">{tier.advice}</p>
          </div>
        </div>
      </div>
      {showUpsell && (
        <div className="pdf-no-print mt-6 rounded-xl border-2 border-amber-400 bg-gradient-to-r from-amber-50 to-orange-50 p-4 shadow-inner">
          <p className="text-sm font-semibold text-amber-950">
            Standardプランの計測上限に達しました。より精密な100点満点の評価と競合比較を行うには、Proプランへアップグレードしてください。
          </p>
          <button
            type="button"
            onClick={() => navigate("/pricing")}
            className="mt-4 inline-flex w-full items-center justify-center rounded-lg bg-amber-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-amber-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600 sm:w-auto"
          >
            プランを確認する
          </button>
        </div>
      )}
    </div>
  );
}
