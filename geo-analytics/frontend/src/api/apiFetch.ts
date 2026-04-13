import { getAccessToken } from "../auth/authSession";
import { DEFAULT_WORKSPACE_TENANT_ID } from "./tenantConstants";

function readXsrfToken(): string {
  if (typeof document === "undefined") {
    return "";
  }
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1].trim()) : "";
}

let csrfPrime: Promise<void> | null = null;

/** Call on logout so the next mutating request re-fetches CSRF for the new session. */
export function resetCsrfPrime(): void {
  csrfPrime = null;
}

function primeCsrfCookie(): Promise<void> {
  if (!csrfPrime) {
    csrfPrime = fetch("/api/csrf", {
      credentials: "include",
      method: "GET",
      headers: {
        "X-Tenant-ID": DEFAULT_WORKSPACE_TENANT_ID,
      },
    }).then(() => undefined);
  }
  return csrfPrime;
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

export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const baseReq = input instanceof Request ? input : null;
  const method = (init.method ?? baseReq?.method ?? "GET").toUpperCase();
  const headers = new Headers(baseReq?.headers);
  new Headers(init.headers ?? undefined).forEach((v, k) => headers.set(k, v));
  headers.set("X-Tenant-ID", DEFAULT_WORKSPACE_TENANT_ID);
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
  return fetch(input, { ...init, credentials: "include", headers });
}

// Re-export for call sites that already import tenant id from apiFetch
export { DEFAULT_WORKSPACE_TENANT_ID } from "./tenantConstants";
