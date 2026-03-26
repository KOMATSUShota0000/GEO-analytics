function readXsrfToken(): string {
  if (typeof document === "undefined") {
    return "";
  }
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return m ? decodeURIComponent(m[1].trim()) : "";
}
export const DEFAULT_WORKSPACE_TENANT_ID = "00000000-0000-0000-0000-000000000000";
export const DEV_BASIC_AUTHORIZATION = "Basic Ym9vdHN0cmFwOmJvb3RzdHJhcA==";
let csrfPrime: Promise<void> | null = null;
function primeCsrfCookie(): Promise<void> {
  if (!csrfPrime) {
    csrfPrime = fetch("/api/csrf", {
      credentials: "include",
      method: "GET",
      headers: {
        "X-Tenant-ID": DEFAULT_WORKSPACE_TENANT_ID,
        Authorization: DEV_BASIC_AUTHORIZATION,
      },
    }).then(() => undefined);
  }
  return csrfPrime;
}
export async function apiFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const baseReq = input instanceof Request ? input : null;
  const method = (init.method ?? baseReq?.method ?? "GET").toUpperCase();
  const headers = new Headers(baseReq?.headers);
  new Headers(init.headers ?? undefined).forEach((v, k) => headers.set(k, v));
  headers.set("X-Tenant-ID", DEFAULT_WORKSPACE_TENANT_ID);
  headers.set("Authorization", DEV_BASIC_AUTHORIZATION);
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
