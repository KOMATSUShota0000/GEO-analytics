import { apiFetch, parseJsonTextAsCamel, responseJsonAsCamel } from "./apiFetch";
import { extractApiErrorCode } from "../types/analysis";
import type { WorkspaceSubscriptionPlan } from "./workspace-api";

export type CheckoutFailureReason = "not_configured" | "temporarily_unavailable" | "already_subscribed";

export type CheckoutSessionResult =
  | { ok: true; url: string }
  | { ok: false; reason: CheckoutFailureReason };

// Why: 設定の問題は何度押しても通らないので、「時間をおいて再度」と案内する失敗と分ける（#167）。
//      理由の分からない失敗（通信エラー等）は、再試行で直る見込みがあるほうに寄せる。
async function failureReasonOf(res: Response): Promise<CheckoutFailureReason> {
  try {
    const code = extractApiErrorCode(parseJsonTextAsCamel(await res.text()));
    if (code === "billing_not_configured") {
      return "not_configured";
    }
    if (code === "billing_already_subscribed") {
      return "already_subscribed";
    }
    return "temporarily_unavailable";
  } catch {
    return "temporarily_unavailable";
  }
}

/**
 * Stripe Checkout（サブスク購入）セッションを作成し、リダイレクト先URLを返す。
 * バックエンド: POST /api/v1/billing/checkout  body: {"plan": "PRO"} → {"url": "https://checkout.stripe.com/..."}
 * 失敗時は失敗の種類（設定の問題／一時的な問題／すでに契約中）を返し、呼び出し側で種類ごとの文言を出す。
 */
export async function createCheckoutSession(
  plan: WorkspaceSubscriptionPlan,
): Promise<CheckoutSessionResult> {
  try {
    const res = await apiFetch("/api/v1/billing/checkout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ plan }),
    });
    if (!res.ok) {
      return { ok: false, reason: await failureReasonOf(res) };
    }
    const raw = (await responseJsonAsCamel(res)) as Record<string, unknown>;
    const url = raw.url;
    return typeof url === "string" && url.length > 0
      ? { ok: true, url }
      : { ok: false, reason: "temporarily_unavailable" };
  } catch {
    return { ok: false, reason: "temporarily_unavailable" };
  }
}
