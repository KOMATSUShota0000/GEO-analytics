import { Lock, Sparkles } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { AnalysisCharts } from "../components/AnalysisCharts";
import { GeoScoreBreakdown } from "../components/analysis/GeoScoreBreakdown";
import type { ScoreBreakdown, TrendData } from "../types/analysis";

// 公開デモは実処理を一切走らせない。説得力のため本番と同じUIコンポーネントに「事前用意のサンプルデータ」を流す。
// 入力値には依存しない固定のデモ結果（誤認防止のため必ず「サンプル」と明示する）。
const SAMPLE_BRAND = "あなたのブランド";

const SAMPLE_BREAKDOWN: ScoreBreakdown = {
  // 旧モデル（後方互換・表示には使わない）
  aiAuditTotal: 38.5,
  meoTotal: 17.0,
  machineReadabilityTotal: 19.5,
  // V13新3軸: content38.5 + technical16.0 + authority12.5 = 67.0
  finalScore: 67.0,
  contentTotal: 38.5,
  technicalTotal: 16.0,
  authorityTotal: 12.5,
  authorityThirdPartyCore: 12.5,
  authorityLocalMeoSub: 0,
  authorityWikipediaKgBonus: 0,
  calculationVersion: "V13_GEO4AXIS",
};

const SAMPLE_TREND: TrendData[] = [
  { date: "2026-03-01", somScore: 41, overallScore: 52 },
  { date: "2026-03-15", somScore: 46, overallScore: 55 },
  { date: "2026-04-01", somScore: 52, overallScore: 60 },
  { date: "2026-04-15", somScore: 58, overallScore: 63 },
  { date: "2026-05-01", somScore: 63, overallScore: 67 },
];

const LOADING_STEPS = [
  "対象ページをクロール中…",
  "LLM回答内での言及を解析中…",
  "第三者言及・権威シグナルを集計中…",
  "4人のAIペルソナが議論中…",
];

type Phase = "input" | "loading" | "result";

export default function PublicDemoPage(): JSX.Element {
  const navigate = useNavigate();
  const [phase, setPhase] = useState<Phase>("input");
  const [url, setUrl] = useState("");
  const [brand, setBrand] = useState("");
  const [step, setStep] = useState(0);
  const timers = useRef<number[]>([]);

  useEffect(() => {
    return () => {
      timers.current.forEach((t) => window.clearTimeout(t));
    };
  }, []);

  const runDemo = () => {
    setPhase("loading");
    setStep(0);
    timers.current.forEach((t) => window.clearTimeout(t));
    timers.current = [];
    LOADING_STEPS.forEach((_, i) => {
      const t = window.setTimeout(() => setStep(i), i * 650);
      timers.current.push(t);
    });
    const done = window.setTimeout(() => setPhase("result"), LOADING_STEPS.length * 650 + 400);
    timers.current.push(done);
  };

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
            LLM回答内での言及率・競合比較を可視化し、ホワイトラベルでそのまま提案資料に。
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
          <section className="mx-auto max-w-xl rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
            <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-slate-200 border-t-indigo-600" />
            <p className="mt-5 text-sm font-medium text-slate-700">{LOADING_STEPS[step]}</p>
            <div className="mt-4 flex justify-center gap-1.5">
              {LOADING_STEPS.map((_, i) => (
                <span
                  key={i}
                  className={[
                    "h-1.5 w-8 rounded-full transition-colors",
                    i <= step ? "bg-indigo-500" : "bg-slate-200",
                  ].join(" ")}
                />
              ))}
            </div>
          </section>
        )}

        {phase === "result" && (
          <section className="mx-auto max-w-5xl space-y-6">
            {/* サンプル明示（誤認防止）。警告調ではなく上品なバッジで正直さと WOW を両立 */}
            <div className="flex justify-center">
              <span className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-500 shadow-sm">
                <span className="h-1.5 w-1.5 rounded-full bg-indigo-400" aria-hidden />
                サンプルレポート（デモ用に用意した例です）
              </span>
            </div>

            {/* 無料で見える「触り」部分 */}
            <GeoScoreBreakdown breakdown={SAMPLE_BREAKDOWN} brandName={brand || SAMPLE_BRAND} />
            <AnalysisCharts
              isPdfMode={false}
              trendData={SAMPLE_TREND}
              brandLabel={`${brand || SAMPLE_BRAND} のインサイト（サンプル）`}
            />

            {/* 旨味部分はぼかし＋ロック → 購入導線 */}
            <div className="relative overflow-hidden rounded-2xl border border-slate-200">
              {/* ぼかし下のコンテンツ（操作不可） */}
              <div
                aria-hidden
                className="pointer-events-none select-none blur-[6px]"
                style={{ filter: "blur(6px)" }}
              >
                <div className="space-y-4 bg-white p-6">
                  <h3 className="text-lg font-bold text-slate-900">競合比較の詳細 ＆ 戦略ロードマップ</h3>
                  <div className="grid gap-3 sm:grid-cols-2">
                    {["AI言及シェアの内訳", "構造化シグナル密度", "エンティティ解像度", "改善優先タスク"].map(
                      (t) => (
                        <div key={t} className="rounded-lg border border-slate-100 bg-slate-50 p-4">
                          <p className="text-sm font-semibold text-slate-700">{t}</p>
                          <p className="mt-2 text-xs text-slate-500">
                            ●●●●●●●●● ●●●●● ●●●●●●● ●●● ●●●●●●●●●● ●●●● ●●●
                          </p>
                        </div>
                      ),
                    )}
                  </div>
                  <div className="rounded-lg border border-slate-100 bg-slate-50 p-4">
                    <p className="text-sm font-semibold text-slate-700">4人のAIペルソナによる議論</p>
                    <p className="mt-2 text-xs text-slate-500">
                      ANALYST: ●●●●●●● ／ INNOVATOR: ●●●●●● ／ SKEPTIC: ●●●●● ／ DIRECTOR: ●●●●●●●
                    </p>
                  </div>
                </div>
              </div>

              {/* オーバーレイ CTA */}
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-white/40 px-4 text-center backdrop-blur-[2px]">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-white shadow-md ring-1 ring-slate-200">
                  <Lock className="h-6 w-6 text-amber-600" aria-hidden />
                </span>
                <h3 className="mt-3 text-lg font-bold text-slate-900">続きはプランでご覧いただけます</h3>
                <p className="mt-1 max-w-md text-sm text-slate-600">
                  競合比較の詳細・改善ロードマップ・4AIペルソナ議論は、有料プランでフルにご利用いただけます。
                </p>
                <button
                  type="button"
                  onClick={() => navigate("/plans")}
                  className="mt-5 rounded-lg bg-indigo-600 px-6 py-3 text-sm font-semibold text-white shadow transition-opacity hover:opacity-90"
                >
                  プランを見る
                </button>
              </div>
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
