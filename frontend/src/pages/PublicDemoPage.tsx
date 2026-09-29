import { Sparkles } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { CompetitorShareChart } from "../components/CompetitorShareChart";
import { LoadingCharacter } from "../components/LoadingCharacter";
import { MinorityReportPanel } from "../components/MinorityReportPanel";
import { RoadmapTimeline } from "../components/RoadmapTimeline";
import { TierDiagnosisCard } from "../components/TierDiagnosisCard";
import { AiRecognitionSection } from "../components/analysis/AiRecognitionSection";
import { GeoScoreBreakdown } from "../components/analysis/GeoScoreBreakdown";
import { JobDiagnosisPanel } from "../components/analysis/JobDiagnosisPanel";
import { RemediationTaskBoard } from "../components/analysis/RemediationTaskBoard";
import {
  DEMO_AI_RECOGNITION,
  DEMO_CONTENT_EVIDENCE,
  DEMO_DEFAULT_BRAND,
  DEMO_DIAGNOSTIC,
  DEMO_MINORITY_REPORTS,
  DEMO_QUERIES,
  DEMO_REMEDIATION_TASKS,
  DEMO_ROADMAP,
  DEMO_SCORE_BREAKDOWN,
  DEMO_SOM_SCORE,
  DEMO_TECHNICAL_EVIDENCE,
  demoCompetitorShares,
} from "../demo/publicDemoSample";

// 公開デモは実処理を一切走らせない。説得力のため本番と同じUIコンポーネントに「事前用意のサンプルデータ」を流す。
// 入力値には依存しない固定のデモ結果（誤認防止のため必ず「サンプル」と明示する）。

/** Why: 本番の解析は数分かかる。デモでは本番と同じ待ち表示を短く見せるだけにし、見る人を待たせない（#188）。 */
const LOADING_MS = 3200;

/** Why: 公開デモからはログイン後の料金画面へ進めないため、上位プランへの誘導は公開のプラン比較へ飛ばす（#188）。 */
const UPGRADE_TO = "/plans";

type Phase = "input" | "loading" | "result";

export default function PublicDemoPage(): JSX.Element {
  const [phase, setPhase] = useState<Phase>("input");
  const [url, setUrl] = useState("");
  const [brand, setBrand] = useState("");
  const timer = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (timer.current !== null) {
        window.clearTimeout(timer.current);
      }
    };
  }, []);

  const runDemo = () => {
    setPhase("loading");
    if (timer.current !== null) {
      window.clearTimeout(timer.current);
    }
    timer.current = window.setTimeout(() => setPhase("result"), LOADING_MS);
  };

  const brandName = brand.trim() || DEMO_DEFAULT_BRAND;

  return (
    <div className="min-h-screen bg-slate-50">
      {/* ヒーロー */}
      <header className="bg-gradient-to-b from-white to-slate-50 px-4 pt-14 pb-8">
        <div className="mx-auto max-w-3xl text-center">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 px-3 py-1 text-xs font-bold text-indigo-700">
            <Sparkles className="h-3.5 w-3.5" aria-hidden />
            SEOの次は、GEO（Generative Engine Optimization）
          </span>
          <h1 className="mt-4 text-3xl font-extrabold tracking-tight text-slate-900 sm:text-4xl">
            あなたのサイトは、AIの回答に
            <br className="hidden sm:block" />
            選ばれていますか？
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-slate-500">
            AIの回答での紹介のされ方と競合との比較を見える化し、ホワイトラベルでそのまま提案資料に。
            代理店・Web制作会社のための高単価GEO提案ツール。
          </p>
        </div>
      </header>

      <main className="px-4 pb-16">
        {phase === "input" && (
          <section className="mx-auto max-w-xl rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="text-lg font-bold text-slate-900">GEO解析レポートのサンプルを見る</h2>
            <p className="mt-1 text-sm text-slate-500">
              URL・ブランド名を入れると、実際のレポートと同じ形式のサンプルをご覧いただけます。
            </p>
            <div className="mt-5 space-y-3">
              <input
                type="url"
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://example.com"
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:border-indigo-500 focus:outline-none"
              />
              <input
                type="text"
                value={brand}
                onChange={(e) => setBrand(e.target.value)}
                placeholder="ブランド名（任意）"
                className="w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:border-indigo-500 focus:outline-none"
              />
              <button
                type="button"
                onClick={runDemo}
                className="w-full rounded-lg bg-indigo-600 px-4 py-3 text-sm font-semibold text-white shadow transition-opacity hover:opacity-90"
              >
                サンプルレポートを見る
              </button>
            </div>
            <p className="mt-3 text-center text-xs text-slate-400">
              ※ デモ用に用意したサンプルデータを表示します（実際の解析は行いません）。
            </p>
          </section>
        )}

        {phase === "loading" && (
          <section className="mx-auto max-w-xl">
            <LoadingCharacter />
          </section>
        )}

        {phase === "result" && (
          <section className="mx-auto max-w-5xl">
            {/* サンプル明示（誤認防止）。警告調ではなく上品なバッジで正直さと WOW を両立 */}
            <div className="mb-6 flex justify-center">
              <span className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                <span className="h-1.5 w-1.5 rounded-full bg-indigo-400" aria-hidden />
                サンプルレポート（デモ用に用意した例です）
              </span>
            </div>

            <JobDiagnosisPanel
              diagnostic={DEMO_DIAGNOSTIC}
              templateFallback={false}
              showTeaser
              upgradeTo={UPGRADE_TO}
            />
            <CompetitorShareChart shares={demoCompetitorShares(brandName)} />
            <RoadmapTimeline items={DEMO_ROADMAP} />
            <MinorityReportPanel reports={DEMO_MINORITY_REPORTS} />
            <div className="mb-6">
              <TierDiagnosisCard somScore={DEMO_SOM_SCORE} isProPlan={false} upgradeTo={UPGRADE_TO} />
            </div>
            <div className="mb-6">
              <RemediationTaskBoard tasks={DEMO_REMEDIATION_TASKS} />
            </div>
            <div className="mb-6">
              <GeoScoreBreakdown
                breakdown={DEMO_SCORE_BREAKDOWN}
                brandName={brandName}
                contentEvidence={DEMO_CONTENT_EVIDENCE}
                technicalEvidence={DEMO_TECHNICAL_EVIDENCE}
              />
            </div>
            <div className="mb-6">
              <AiRecognitionSection summary={DEMO_AI_RECOGNITION} queries={DEMO_QUERIES} />
            </div>

            <div className="text-center">
              <button
                type="button"
                onClick={() => setPhase("input")}
                className="text-sm font-medium text-slate-500 hover:text-slate-700"
              >
                ↻ もう一度試す
              </button>
            </div>
          </section>
        )}

        {/* フッター導線 */}
        <div className="mx-auto mt-10 max-w-5xl text-center">
          <RouterLink to="/plans" className="text-sm font-medium text-indigo-600 hover:text-indigo-800">
            プラン・料金を見る →
          </RouterLink>
        </div>
      </main>
    </div>
  );
}
