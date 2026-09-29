import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useLocation } from "react-router-dom";
import { useBranding } from "../branding/useBranding";
import {
  changeWorkspacePlan,
  fetchWorkspaceBillingStatus,
  fetchWorkspacePlan,
  type WorkspaceBillingStatus,
  type WorkspaceSubscriptionPlan,
} from "../api/workspace-api";
import { PlanComparison, type PlanSpec } from "../pricing/planCatalog";
import { createCheckoutSession, type CheckoutFailureReason } from "../api/billing-api";
import { resolvePricingReturnTo } from "../pricing/pricingReturn";

// Stripe 連携までは mailto: で問い合わせを受ける
const CONTACT_EMAIL = "hariboikatu.2525@gmail.com";
const DEMO_MAILTO = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent("GEO Analytics Proプランのデモ申し込み")}`;
const INQUIRY_MAILTO = `mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent("GEO Analytics プランについてのお問い合わせ")}`;

const CHECKOUT_FAILURE_MESSAGES: Record<CheckoutFailureReason, string> = {
  not_configured:
    "現在、オンラインでのお申し込みを受け付けられません。お手数ですが、画面下の「お問い合わせ」からご連絡ください。",
  temporarily_unavailable: "決済ページを開けませんでした。少し時間をおいて、もう一度お試しください。",
  already_subscribed: "すでにご契約中です。プランの変更は、画面下の「お問い合わせ」からご連絡ください。",
};

const INQUIRY_SECTION_ID = "pricing-inquiry";

function PlanSwitcher({ onChanged }: { onChanged: () => void }): JSX.Element {
  const [currentPlan, setCurrentPlan] = useState<WorkspaceSubscriptionPlan | null>(null);
  const [selected, setSelected] = useState<WorkspaceSubscriptionPlan>("STANDARD");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const plan = await fetchWorkspacePlan();
    if (plan) {
      setCurrentPlan(plan);
      setSelected(plan);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const onSubmit = async () => {
    if (selected === currentPlan) return;
    setSubmitting(true);
    setError(null);
    const ok = await changeWorkspacePlan(selected);
    setSubmitting(false);
    if (ok) {
      await reload();
      onChanged();
    } else {
      setError("プラン切替に失敗しました。再度お試しください。");
    }
  };

  return (
    <div className="mx-auto mt-6 max-w-5xl rounded-xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm font-semibold text-slate-700">現在のプラン:</span>
        <span className="inline-flex items-center rounded-full bg-slate-900 px-3 py-0.5 text-xs font-bold text-white">
          {currentPlan ?? "読み込み中..."}
        </span>
        <span className="ml-2 text-sm text-slate-500">切替先:</span>
        <select
          aria-label="プラン切替"
          value={selected}
          onChange={(e) => setSelected(e.target.value as WorkspaceSubscriptionPlan)}
          className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm"
          disabled={submitting}
        >
          <option value="STANDARD">STANDARD</option>
          <option value="PRO">PRO</option>
          <option value="EXPERT">EXPERT</option>
        </select>
        <button
          type="button"
          onClick={onSubmit}
          disabled={submitting || selected === currentPlan}
          className="rounded-md bg-slate-900 px-3 py-1 text-sm font-semibold text-white disabled:opacity-40"
        >
          {submitting ? "切替中..." : "切替"}
        </button>
      </div>
      {error && <p className="mt-2 text-xs text-red-600">{error}</p>}
      <p className="mt-2 text-xs text-slate-400">
        開発・検証用のプラン切替です。本番では Stripe 連携が入る予定です。
      </p>
    </div>
  );
}

export default function PricingPage(): JSX.Element {
  const { brandColor } = useBranding();
  const returnTo = resolvePricingReturnTo(useLocation().state);
  const [checkoutPlan, setCheckoutPlan] = useState<WorkspaceSubscriptionPlan | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [billing, setBilling] = useState<WorkspaceBillingStatus | null>(null);
  const [billingLoaded, setBillingLoaded] = useState(false);

  const reloadBilling = useCallback(async () => {
    setBilling(await fetchWorkspaceBillingStatus());
    setBillingLoaded(true);
  }, []);

  useEffect(() => {
    void reloadBilling();
  }, [reloadBilling]);

  const scrollToInquiry = () => {
    document.getElementById(INQUIRY_SECTION_ID)?.scrollIntoView({ behavior: "smooth" });
  };

  const startCheckout = async (plan: WorkspaceSubscriptionPlan) => {
    setCheckoutError(null);
    setCheckoutPlan(plan);
    const result = await createCheckoutSession(plan);
    if (result.ok) {
      window.location.href = result.url;
      return;
    }
    setCheckoutPlan(null);
    setCheckoutError(CHECKOUT_FAILURE_MESSAGES[result.reason]);
  };

  return (
    <div className="min-h-screen bg-slate-50 py-10 px-4">
      {/* ヘッダー */}
      <div className="mx-auto max-w-5xl text-center">
        <h1 className="text-3xl font-extrabold text-slate-900">GEO解析プランを選ぶ</h1>
        <p className="mt-2 text-slate-500">LLM回答内での言及率・競合比較精度を最大化するプラン</p>
      </div>

      {/* プラン切替は開発・検証用。本番ビルドでは実ユーザーに見せない（import.meta.env.DEV ガード）。 */}
      {import.meta.env.DEV ? <PlanSwitcher onChanged={() => void reloadBilling()} /> : null}

      {/* コンテキストバナー */}
      <div className="mx-auto mt-6 max-w-5xl rounded-xl border border-amber-200 bg-amber-50 px-5 py-4">
        <p className="text-sm text-amber-900">
          SoM スコアが向上しています。より精密な競合比較と100点満点評価で、次のGEO戦略を立案しましょう。
        </p>
      </div>

      {checkoutError ? (
        <div className="mx-auto mt-4 max-w-5xl rounded-lg border border-red-200 bg-red-50 px-4 py-2 text-center text-sm text-red-700">
          {checkoutError}
        </div>
      ) : null}

      {/* プラン比較カード＋機能比較テーブル（共有モジュール）。CTA は Stripe Checkout を起動 */}
      <PlanComparison
        currentPlan={billing?.plan ?? null}
        renderCardCta={(plan: PlanSpec) => {
          if (billing?.plan === plan.key) {
            return (
              <button
                type="button"
                disabled
                className="block w-full rounded-lg border border-slate-200 bg-slate-100 px-4 py-2.5 text-center text-sm font-semibold text-slate-500"
              >
                現在のプラン
              </button>
            );
          }
          if (billing?.hasStripeSubscription) {
            return (
              <button
                type="button"
                onClick={scrollToInquiry}
                className="block w-full rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-center text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
              >
                プラン変更はお問い合わせ
              </button>
            );
          }
          return (
            <button
              type="button"
              onClick={() => void startCheckout(plan.key)}
              disabled={checkoutPlan !== null || !billingLoaded}
              className="block w-full rounded-lg px-4 py-2.5 text-center text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
              style={{ backgroundColor: brandColor }}
            >
              {checkoutPlan === plan.key ? "決済ページへ移動中…" : "このプランで申し込む"}
            </button>
          );
        }}
      />

      {/* CTAセクション */}
      <div
        id={INQUIRY_SECTION_ID}
        className="mx-auto mt-10 max-w-5xl scroll-mt-6 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm text-center"
      >
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
          to={returnTo ?? "/"}
          className="text-sm font-medium text-indigo-600 hover:text-indigo-800"
        >
          {returnTo ? "← 解析結果に戻る" : "← ジョブ解析に戻る"}
        </RouterLink>
      </div>
    </div>
  );
}
