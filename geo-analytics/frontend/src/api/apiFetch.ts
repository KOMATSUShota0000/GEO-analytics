import { clearAccessToken, getAccessToken, tryRestoreSession } from "../auth/authSession";
import { clearClientState } from "../auth/logout";
import type { RefreshFailureReason } from "../types/auth";
import { DEFAULT_WORKSPACE_TENANT_ID } from "./tenantConstants";

let isRefreshing = false;
let refreshSubscribers: ((accessToken: string) => void)[] = [];
let refreshFailureHandlers: Array<(reason: RefreshFailureReason) => void> = [];

function onRefreshed(accessToken: string): void {
  refreshSubscribers.forEach((callback) => callback(accessToken));
  refreshSubscribers = [];
}

function addRefreshSubscriber(callback: (accessToken: string) => void): void {
  refreshSubscribers.push(callback);
}

/** Playwright / print route: workspace tenant from injected storage (see ReportPrintPage). */
let pdfPrintTenantOverride: string | null = null;

export function setPdfPrintTenantIdOverride(tenantId: string | null): void {
  pdfPrintTenantOverride = tenantId != null && tenantId.trim().length > 0 ? tenantId.trim() : null;
}

function resolveTenantHeader(): string {
  if (typeof window !== "undefined") {
    const w = window as unknown as { __PDF_TENANT_ID__?: string };
    const tid = w.__PDF_TENANT_ID__;
    if (typeof tid === "string" && tid.trim().length > 0) {
      return tid.trim();
    }
  }
  return pdfPrintTenantOverride ?? DEFAULT_WORKSPACE_TENANT_ID;
}

function readXsrfToken(): string {
  if (typeof document === "undefined") {
    return "";
  }
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1].trim()) : "";
}

let csrfPrimePromise: Promise<void> | null = null;

/** Call on logout so the next mutating request re-fetches CSRF for the new session. */
export function resetCsrfPrime(): void {
  csrfPrimePromise = null;
}

function primeCsrfCookie(): Promise<void> {
  if (csrfPrimePromise !== null) {
    return csrfPrimePromise;
  }
  const p = fetch("/api/csrf", {
    credentials: "include",
    method: "GET",
    headers: {
      "X-Tenant-ID": resolveTenantHeader(),
    },
  })
    .then(() => undefined)
    .finally(() => {
      csrfPrimePromise = null;
    });
  csrfPrimePromise = p;
  return p;
}

function snakeToCamelKey(key: string): string {
  return key.replace(/_([a-zA-Z0-9])/g, (_, ch: string) => ch.toUpperCase());
}

export function keysToCamelDeep(value: unknown): unknown {
  if (value === null || typeof value !== "object") {
    return value;
  }
  if (Array.isArray(value)) {
    return value.map(keysToCamelDeep);
  }
  const src = value as Record<string, unknown>;
  const out: Record<string, unknown> = {};
  for (const k of Object.keys(src)) {
    out[snakeToCamelKey(k)] = keysToCamelDeep(src[k]);
  }
  return out;
}

export function parseJsonTextAsCamel(text: string): unknown {
  const t = text.trim();
  if (t.length === 0) {
    return null;
  }
  return keysToCamelDeep(JSON.parse(t) as unknown);
}

export async function responseJsonAsCamel(response: Response): Promise<unknown> {
  const text = await response.text();
  return parseJsonTextAsCamel(text);
}

/**
 * @param accessTokenOverride リフレッシュ直後の JWT。省略時は {@link getAccessToken}（リトライでは必ず明示すること）。
 */
async function reapplyAuthHeadersAfterRefresh(
  headers: Headers,
  unsafe: boolean,
  accessTokenOverride?: string | null,
): Promise<void> {
  const fresh =
    accessTokenOverride !== undefined && accessTokenOverride !== null
      ? accessTokenOverride
      : getAccessToken();
  if (fresh !== null && fresh.length > 0) {
    headers.set("Authorization", `Bearer ${fresh}`);
  } else {
    headers.delete("Authorization");
  }
  if (unsafe) {
    resetCsrfPrime();
    await primeCsrfCookie();
    const xsrf = readXsrfToken();
    if (xsrf.length > 0) {
      headers.set("X-XSRF-TOKEN", xsrf);
    } else {
      headers.delete("X-XSRF-TOKEN");
    }
  }
}

/** 同一 Request オブジェクトを二度 fetch するとヘッダ上書きが無視されることがあるため、URL 文字列で再試行する。 */
function resolveFetchUrl(input: RequestInfo | URL): string {
  if (typeof input === "string") {
    return input;
  }
  if (input instanceof URL) {
    return input.href;
  }
  return input.url;
}

/**
 * 401 後の再試行用。init の headers と競合しないよう、確定した Bearer を持つ Headers で組み立てる。
 */
function buildRetryInit(input: RequestInfo | URL, init: RequestInit, authHeaders: Headers): RequestInit {
  return {
    ...init,
    method: init.method ?? (input instanceof Request ? input.method : undefined),
    credentials: "include",
    headers: new Headers(authHeaders),
    signal: init.signal ?? (input instanceof Request ? input.signal : undefined),
  };
}

function notifyRefreshFailedAndClearWaiters(reason: RefreshFailureReason): void {
  refreshSubscribers = [];
  refreshFailureHandlers.splice(0).forEach((handler) => handler(reason));
  refreshFailureHandlers = [];
}

export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const isPdfMode =
    typeof window !== "undefined" &&
    window.location.pathname.includes("/reports/print") &&
    window.location.search.includes("internal_token");
  const baseReq = input instanceof Request ? input : null;
  const method = (init.method ?? baseReq?.method ?? "GET").toUpperCase();
  const headers = new Headers(baseReq?.headers);
  new Headers(init.headers ?? undefined).forEach((v, k) => headers.set(k, v));
  const resolvedBody = init.body ?? (baseReq !== null ? baseReq.body : undefined);
  if (resolvedBody instanceof FormData) {
    headers.delete("Content-Type");
  }
  headers.set("X-Tenant-ID", resolveTenantHeader());
  const accessToken = getAccessToken();
  if (accessToken !== null && accessToken.length > 0) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  const unsafe = !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method);
  if (unsafe) {
    await primeCsrfCookie();
    const token = readXsrfToken();
    if (token.length > 0) {
      headers.set("X-XSRF-TOKEN", token);
    }
  }

  const fetchInit: RequestInit = { ...init, credentials: "include", headers };
  let response = await fetch(input, fetchInit);

  if (response.status === 401) {
    if (isPdfMode) {
      return response;
    }
    if (!isRefreshing) {
      isRefreshing = true;
      try {
        const result = await tryRestoreSession({ force: true });
        if (result.success) {
          const newToken = getAccessToken();
          if (newToken !== null && newToken.length > 0) {
            refreshFailureHandlers = [];
            onRefreshed(newToken);
            await reapplyAuthHeadersAfterRefresh(headers, unsafe, newToken);
            const retryUrl = resolveFetchUrl(input);
            const retryInit = buildRetryInit(input, init, headers);
            response = await fetch(retryUrl, retryInit);
          } else {
            notifyRefreshFailedAndClearWaiters("unknown");
            clearClientState();
            clearAccessToken();
            if (typeof window !== "undefined") {
              window.location.href = `/login?reason=${encodeURIComponent("unknown")}`;
            }
            return new Promise<Response>(() => {});
          }
        } else {
          if (result.reason === "network_error") {
            notifyRefreshFailedAndClearWaiters("network_error");
            isRefreshing = false;
            throw new Error("Network connection lost.");
          }
          notifyRefreshFailedAndClearWaiters(result.reason);
          clearClientState();
          clearAccessToken();
          if (typeof window !== "undefined") {
            window.location.href = `/login?reason=${encodeURIComponent(result.reason)}`;
          }
          return new Promise<Response>(() => {});
        }
      } finally {
        isRefreshing = false;
      }
    } else {
      return new Promise<Response>((resolve, reject) => {
        refreshFailureHandlers.push((reason) => {
          if (reason === "network_error") {
            reject(new Error(`セッション更新失敗: ${reason}`));
          }
        });
        addRefreshSubscriber((newTokenFromRefresh) => {
          void (async () => {
            try {
              await reapplyAuthHeadersAfterRefresh(headers, unsafe, newTokenFromRefresh);
              const retryUrl = resolveFetchUrl(input);
              const retryInit = buildRetryInit(input, init, headers);
              resolve(await fetch(retryUrl, retryInit));
            } catch (err: unknown) {
              reject(err);
            }
          })();
        });
      });
    }
  }

  return response;
}

// Re-export for call sites that already import tenant id from apiFetch
export { DEFAULT_WORKSPACE_TENANT_ID } from "./tenantConstants";
