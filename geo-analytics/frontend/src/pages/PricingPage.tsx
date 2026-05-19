import { Link as RouterLink } from "react-router-dom";
import { Check, Minus, Sparkles } from "lucide-react";
import { useBranding } from "../branding/useBranding";

// Stripe 連携までは mailto: で問い合わせを受ける
const CONTACT_EMAIL = "hariboikatu.2525@gmail.com";
const DEMO_MAILTO = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent("GEO Analytics Proプランのデモ申し込み")}`;
const INQUIRY_MAILTO = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent("GEO Analytics プランについてのお問い合わせ")}`;

type PlanKey = "STANDARD" | "PRO" | "EXPERT";

interface PlanSpec {
  key: PlanKey;
  label: string;
  dailyLimit: number;
  totalLimit: number;
  realtimeBatchMax: number;
  maxCompetitorEvidenceXmlChars: number;
  aiModels: string;
  proFeatures: boolean;
  whiteLabel: boolean;
}

// SubscriptionPlan.java の定義に準拠した静的定数
const PLANS: PlanSpec[] = [
  {
    key: "STANDARD",
    label: "Standard",
    dailyLimit: 10,
    totalLimit: 10,
    realtimeBatchMax: 10,
    maxCompetitorEvidenceXmlChars: 12_000,
    aiModels: "Gemini のみ",
    proFeatures: false,
    whiteLabel: true,
  },
  {
    key: "PRO",
    label: "Pro",
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
    dailyLimit: 200,
    totalLimit: 2_000,
    realtimeBatchMax: 50,
    maxCompetitorEvidenceXmlChars: 48_000,
    aiModels: "全モデル（Claude 含む）",
    proFeatures: true,
    whiteLabel: true,
  },
];

interface FeatureRow {
  label: string;
  render: (plan: PlanSpec) => React.ReactNode;
}

const FEATURE_ROWS: FeatureRow[] = [
  {
    label: "1日の解析上限",
    render: (p) => `${p.dailyLimit}回`,
  },
  {
    label: "累計解析上限",
    render: (p) => `${p.totalLimit.toLocaleString()}件`,
  },
  {
    label: "リアルタイム一括上限",
    render: (p) => `${p.realtimeBatchMax}件`,
  },
  {
    label: "競合エビデンス量",
    render: (p) => `${(p.maxCompetitorEvidenceXmlChars / 1000).toFixed(0)}k 文字`,
  },
  {
    label: "使用AIモデル",
    render: (p) => p.aiModels,
  },
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

function PlanCard({ plan, brandColor }: { plan: PlanSpec; brandColor: string }): JSX.Element {
  const isPro = plan.key === "PRO";
  return (
    <div
      className={[
        "relative flex flex-col rounded-2xl bg-white p-6 shadow-sm",
        isPro
          ? "border-2 border-amber-400"
          : "border border-slate-200",
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
      <ul className="mt-4 space-y-2 text-sm text-slate-600">
        <li>1日 {plan.dailyLimit}回</li>
        <li>累計 {plan.totalLimit.toLocaleString()}件</li>
        <li>競合エビデンス {(plan.maxCompetitorEvidenceXmlChars / 1000).toFixed(0)}k 文字</li>
        <li>{plan.aiModels}</li>
      </ul>
      {plan.key !== "STANDARD" && (
        <a
          href={DEMO_MAILTO}
          className="mt-6 block rounded-lg px-4 py-2.5 text-center text-sm font-semibold text-white transition-opacity hover:opacity-90"
          style={{ backgroundColor: brandColor }}
        >
          このプランを検討する
        </a>
      )}
    </div>
  );
}

export default function PricingPage(): JSX.Element {
  const { brandColor } = useBranding();

  return (
    <div className="min-h-screen bg-slate-50 py-10 px-4">
      {/* ヘッダー */}
      <div className="mx-auto max-w-5xl text-center">
        <h1 className="text-3xl font-extrabold text-slate-900">GEO解析プランを選ぶ</h1>
        <p className="mt-2 text-slate-500">LLM回答内での言及率・競合比較精度を最大化するプラン</p>
      </div>

      {/* コンテキストバナー */}
      <div className="mx-auto mt-6 max-w-5xl rounded-xl border border-amber-200 bg-amber-50 px-5 py-4">
        <p className="text-sm text-amber-900">
          SoM スコアが向上しています。より精密な競合比較と100点満点評価で、次のGEO戦略を立案しましょう。
        </p>
      </div>

      {/* プラン比較カード */}
      <div className="mx-auto mt-8 max-w-5xl grid gap-6 sm:grid-cols-3">
        {PLANS.map((plan) => (
          <PlanCard key={plan.key} plan={plan} brandColor={brandColor} />
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
              <tr
                key={row.label}
                className={idx % 2 === 0 ? "bg-white" : "bg-slate-50/60"}
              >
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

      {/* CTAセクション */}
      <div className="mx-auto mt-10 max-w-5xl rounded-2xl border border-slate-200 bg-white p-8 shadow-sm text-center">
        <h2 className="text-xl font-bold text-slate-900">
          プランのアップグレードについてお問い合わせください
        </h2>
        <p className="mt-2 text-sm text-slate-500">
          企業・代理店向けのカスタムプランもご相談いただけます。
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-4">
          <a
            href={DEMO_MAILTO}
            className="rounded-lg px-6 py-3 text-sm font-semibold text-white shadow transition-opacity hover:opacity-90"
            style={{ backgroundColor: brandColor }}
          >
            デモを申し込む
          </a>
          <a
            href={INQUIRY_MAILTO}
            className="rounded-lg border border-slate-300 bg-white px-6 py-3 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
          >
            お問い合わせ
          </a>
        </div>
      </div>

      {/* 戻るリンク */}
      <div className="mx-auto mt-8 max-w-5xl text-center">
        <RouterLink
          to="/"
          className="text-sm font-medium text-indigo-600 hover:text-indigo-800"
        >
          ← ジョブ解析に戻る
        </RouterLink>
      </div>
    </div>
  );
}
