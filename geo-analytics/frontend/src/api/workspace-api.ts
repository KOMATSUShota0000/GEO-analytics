import { apiFetch, responseJsonAsCamel } from "./apiFetch";
import { DEFAULT_WORKSPACE_TENANT_ID } from "./tenantConstants";

export type WorkspaceSubscriptionPlan = "STANDARD" | "PRO" | "EXPERT";

export type WorkspacePayload = {
  subscription_plan: WorkspaceSubscriptionPlan;
};

/**
 * デフォルトワークスペース（テナント固定の現実装）のプランを取得する。
 * 認証失敗・ネットワークエラー時は null を返し、呼び出し側でフォールバックさせる。
 */
export async function fetchWorkspacePlan(): Promise<WorkspaceSubscriptionPlan | null> {
  try {
    const res = await apiFetch(`/api/v1/workspaces/${DEFAULT_WORKSPACE_TENANT_ID}`);
    if (!res.ok) {
      return null;
    }
    const raw = (await responseJsonAsCamel(res)) as Record<string, unknown>;
    // snake_case → camelCase 変換後は subscriptionPlan になる
    const plan = raw.subscriptionPlan;
    if (plan === "STANDARD" || plan === "PRO" || plan === "EXPERT") {
      return plan;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * デフォルトワークスペースのプランを切り替える（開発・検証用）。
 * バックエンド: PATCH /api/v1/workspaces/{id}/subscription  body: {"plan": "PRO"}
 */
export async function changeWorkspacePlan(plan: WorkspaceSubscriptionPlan): Promise<boolean> {
  try {
    const res = await apiFetch(
      `/api/v1/workspaces/${DEFAULT_WORKSPACE_TENANT_ID}/subscription`,
      {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ plan }),
      },
    );
    return res.ok;
  } catch {
    return false;
  }
}
