import type { Location } from "react-router-dom";

export type PricingReturnState = { returnTo: string };

export function pricingReturnState(location: Location): PricingReturnState {
  return { returnTo: `${location.pathname}${location.search}` };
}

/**
 * router state は履歴経由で任意の値が入り得るため、解析結果画面の内部パスだけを戻り先として受け付ける。
 */
export function resolvePricingReturnTo(state: unknown): string | null {
  if (typeof state !== "object" || state === null) {
    return null;
  }
  const returnTo = (state as Record<string, unknown>).returnTo;
  if (typeof returnTo !== "string" || !returnTo.startsWith("/job/")) {
    return null;
  }
  return returnTo;
}
