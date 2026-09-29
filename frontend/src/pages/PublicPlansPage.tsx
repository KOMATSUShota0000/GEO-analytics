import { Link as RouterLink, useNavigate } from "react-router-dom";
import { PlanComparison, type PlanSpec } from "../pricing/planCatalog";
import { IS_PUBLIC_SITE } from "../publicSite";

// 公開（未ログイン）ページは WorkspaceBrandingProvider の外にあるため、
// テナント別ブランディングに依存せず既定ブランドカラーで成立させる（CSS フォールバックと一致）。
const DEFAULT_BRAND_COLOR = "#4f46e5";

/**
 * 公開プラン購入ページ（`/plans`・認証不要）。
 * フェーズ1: 「このプランで始める」CTA はサインアップ（/login）へ誘導する導線のガワ。
 * フェーズ2（Stripe）で、ログイン済みユーザーの当ボタンが Checkout Session 起動に接続される。
 */
export default function PublicPlansPage(): JSX.Element {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-slate-50 py-10 px-4">
      {/* ヘッダー */}
      <div className="mx-auto max-w-5xl text-center">
        <h1 className="text-3xl font-extrabold text-slate-900">GEO解析プランを選ぶ</h1>
        <p className="mt-2 text-slate-500">
          LLM回答内での言及率・競合比較精度を最大化するプラン
        </p>
      </div>

      {/* プラン比較カード＋機能比較テーブル（共有モジュール） */}
      <PlanComparison
        renderCardCta={(plan: PlanSpec) =>
          IS_PUBLIC_SITE ? (
            <button
              type="button"
              disabled
              className="block w-full cursor-not-allowed rounded-lg bg-slate-200 px-4 py-2.5 text-center text-sm font-semibold text-slate-600"
            >
              デモ版では申し込めません
            </button>
          ) : (
            <button
              type="button"
              onClick={() => navigate("/login")}
              className="block w-full rounded-lg px-4 py-2.5 text-center text-sm font-semibold text-white transition-opacity hover:opacity-90"
              style={{
                backgroundColor: plan.key === "STANDARD" ? "#475569" : DEFAULT_BRAND_COLOR,
              }}
            >
              このプランで始める
            </button>
          )
        }
      />

      {/* 補足 */}
      <div className="mx-auto mt-8 max-w-5xl text-center">
        <p className="text-xs text-slate-400">
          {IS_PUBLIC_SITE
            ? "このページは紹介用のデモ版です。実際のお申し込みはできません。"
            : "「このプランで始める」からアカウント登録に進みます。お支払いはご登録後に安全な決済ページでお手続きいただけます。"}
        </p>
      </div>

      {/* 導線リンク */}
      <div className="mx-auto mt-6 max-w-5xl text-center">
        <RouterLink to="/demo" className="text-sm font-medium text-indigo-600 hover:text-indigo-800">
          ← デモを試す
        </RouterLink>
      </div>
    </div>
  );
}
