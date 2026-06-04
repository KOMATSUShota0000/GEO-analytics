import { apiFetch, responseJsonAsCamel } from "./apiFetch";
import type { WorkspaceSubscriptionPlan } from "./workspace-api";

/**
 * Stripe Checkout（サブスク購入）セッションを作成し、リダイレクト先URLを返す。
 * バックエンド: POST /api/v1/billing/checkout  body: {"plan": "PRO"} → {"url": "https://checkout.stripe.com/..."}
 * 失敗時は null を返し、呼び出し側でエラー表示する。
 */
export async function createCheckoutSession(
  plan: WorkspaceSubscriptionPlan,
): Promise<string | null> {
  try {
    const res = await apiFetch("/api/v1/billing/checkout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ plan }),
    });
    if (!res.ok) {
      return null;
    }
    const raw = (await responseJsonAsCamel(res)) as Record<string, unknown>;
    const url = raw.url;
    return typeof url === "string" && url.length > 0 ? url : null;
  } catch {
    return null;
  }
}
