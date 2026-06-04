import { Check, Minus, Sparkles } from "lucide-react";
import type { ReactNode } from "react";

// 公開ページ（未ログイン）とログイン内 PricingPage で共有するプラン定義の単一ソース。
// SubscriptionPlan.java の定義に準拠した静的定数。重複させないこと。

export type PlanKey = "STANDARD" | "PRO" | "EXPERT";

export interface PlanSpec {
  key: PlanKey;
  label: string;
  /** 月額・税抜・円。0 は無料。product-overview.md §4 の確定値（2026-06-03 オーナー承認）。 */
  monthlyPriceYen: number;
  dailyLimit: number;
  totalLimit: number;
  realtimeBatchMax: number;
  maxCompetitorEvidenceXmlChars: number;
  aiModels: string;
  proFeatures: boolean;
  whiteLabel: boolean;
}

export const PLANS: PlanSpec[] = [
  {
    key: "STANDARD",
    label: "Standard",
    monthlyPriceYen: 9_800,
    dailyLimit: 10,
    totalLimit: 10,
    realtimeBatchMax: 10,
    maxCompetitorEvidenceXmlChars: 12_000,
    aiModels: "Gemini のみ",
    proFeatures: false,
    whiteLabel: false,
  },
  {
    key: "PRO",
    label: "Pro",
    monthlyPriceYen: 29_800,
    dailyLimit: 100,
    totalLimit: 500,
    realtimeBatchMax: 50,
    maxCompetitorEvidenceXmlChars: 24_000,
    aiModels: "Gemini + ChatGPT",
    proFeatures: true,
    whiteLabel: true,
  },
  {
    key: "EXPERT",
    label: "Expert",
    monthlyPriceYen: 59_800,
    dailyLimit: 200,
    totalLimit: 2_000,
    realtimeBatchMax: 50,
    maxCompetitorEvidenceXmlChars: 48_000,
    aiModels: "全モデル（Claude 含む）",
    proFeatures: true,
    whiteLabel: true,
  },
];

/** 月額料金の表示用フォーマット（0 は「無料」）。 */
export function formatMonthlyPrice(plan: PlanSpec): string {
  return plan.monthlyPriceYen === 0 ? "無料" : `¥${plan.monthlyPriceYen.toLocaleString()}`;
}

interface FeatureRow {
  label: string;
  render: (plan: PlanSpec) => ReactNode;
}

export const FEATURE_ROWS: FeatureRow[] = [
  {
    label: "月額（税抜）",
    render: (p) => (
      <span className="font-semibold text-slate-900">
        {formatMonthlyPrice(p)}
        {p.monthlyPriceYen > 0 ? " /月" : ""}
      </span>
    ),
  },
  { label: "1日の解析上限", render: (p) => `${p.dailyLimit}回` },
  { label: "累計解析上限", render: (p) => `${p.totalLimit.toLocaleString()}件` },
  { label: "リアルタイム一括上限", render: (p) => `${p.realtimeBatchMax}件` },
  {
    label: "競合エビデンス量",
    render: (p) => `${(p.maxCompetitorEvidenceXmlChars / 1000).toFixed(0)}k 文字`,
  },
  { label: "使用AIモデル", render: (p) => p.aiModels },
  {
    label: "Proプラン機能",
    render: (p) =>
      p.proFeatures ? (
        <Check className="mx-auto h-5 w-5 text-emerald-600" aria-label="あり" />
      ) : (
        <Minus className="mx-auto h-5 w-5 text-slate-300" aria-label="なし" />
      ),
  },
  {
    label: "ホワイトラベル",
    render: (p) =>
      p.whiteLabel ? (
        <Check className="mx-auto h-5 w-5 text-emerald-600" aria-label="あり" />
      ) : (
        <Minus className="mx-auto h-5 w-5 text-slate-300" aria-label="なし" />
      ),
  },
];

export interface PlanComparisonProps {
  /** 各プランカードの CTA。プラン購入導線をページごとに差し替える（mailto / Stripe Checkout 等）。 */
  renderCardCta?: (plan: PlanSpec) => ReactNode;
}

function PlanCard({
  plan,
  renderCardCta,
}: {
  plan: PlanSpec;
  renderCardCta?: (plan: PlanSpec) => ReactNode;
}): JSX.Element {
  const isPro = plan.key === "PRO";
  return (
    <div
      className={[
        "relative flex flex-col rounded-2xl bg-white p-6 shadow-sm",
        isPro ? "border-2 border-amber-400" : "border border-slate-200",
      ].join(" ")}
    >
      {isPro && (
        <div className="absolute -top-3.5 left-1/2 -translate-x-1/2">
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-400 px-3 py-0.5 text-xs font-bold text-white shadow">
            <Sparkles className="h-3 w-3" aria-hidden />
            おすすめ
          </span>
        </div>
      )}
      <h3 className="text-lg font-bold text-slate-900">{plan.label}</h3>
      <div className="mt-2 flex items-baseline gap-1">
        <span className="text-2xl font-extrabold text-slate-900">{formatMonthlyPrice(plan)}</span>
        {plan.monthlyPriceYen > 0 && (
          <span className="text-sm font-medium text-slate-500">/月（税抜）</span>
        )}
      </div>
      <ul className="mt-4 space-y-2 text-sm text-slate-600">
        <li>1日 {plan.dailyLimit}回</li>
        <li>累計 {plan.totalLimit.toLocaleString()}件</li>
        <li>競合エビデンス {(plan.maxCompetitorEvidenceXmlChars / 1000).toFixed(0)}k 文字</li>
        <li>{plan.aiModels}</li>
      </ul>
      {renderCardCta ? <div className="mt-6">{renderCardCta(plan)}</div> : null}
    </div>
  );
}

/** プラン比較カード＋機能比較テーブル。公開 /plans とログイン内 PricingPage で共有する。 */
export function PlanComparison({ renderCardCta }: PlanComparisonProps): JSX.Element {
  return (
    <>
      {/* プラン比較カード */}
      <div className="mx-auto mt-8 max-w-5xl grid gap-6 sm:grid-cols-3">
        {PLANS.map((plan) => (
          <PlanCard key={plan.key} plan={plan} renderCardCta={renderCardCta} />
        ))}
      </div>

      {/* 機能比較テーブル */}
      <div className="mx-auto mt-10 max-w-5xl overflow-x-auto rounded-2xl border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-100">
              <th className="px-4 py-3 text-left font-semibold text-slate-700 w-1/4">機能</th>
              {PLANS.map((plan) => (
                <th
                  key={plan.key}
                  className={[
                    "px-4 py-3 text-center font-semibold w-1/4",
                    plan.key === "PRO" ? "text-amber-700" : "text-slate-700",
                  ].join(" ")}
                >
                  {plan.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {FEATURE_ROWS.map((row, idx) => (
              <tr key={row.label} className={idx % 2 === 0 ? "bg-white" : "bg-slate-50/60"}>
                <td className="px-4 py-3 text-slate-600">{row.label}</td>
                {PLANS.map((plan) => (
                  <td key={plan.key} className="px-4 py-3 text-center text-slate-700">
                    {row.render(plan)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
