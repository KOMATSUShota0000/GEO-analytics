import { DEFAULT_WORKSPACE_TENANT_ID } from "../api/tenantConstants";

export const ACCESS_TOKEN_STORAGE_KEY = "geo_analytics.access_token";
export const TENANT_STORAGE_KEY = "geo_analytics.tenant_id";

let memoryToken: string | null = null;
let hydratedFromStorage = false;

function readSessionStorageToken(): string | null {
  if (typeof sessionStorage === "undefined") {
    return null;
  }
  try {
    const raw = sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    return raw !== null && raw.length > 0 ? raw : null;
  } catch {
    return null;
  }
}

function readLocalStorageToken(): string | null {
  if (typeof localStorage === "undefined") {
    return null;
  }
  try {
    const raw = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);
    return raw !== null && raw.length > 0 ? raw : null;
  } catch {
    return null;
  }
}

/**
 * Token resolution for the in-memory cache (first load only).
 * Prefer sessionStorage (normal login) over localStorage (PDF init script may write both).
 * Do not read {@code window.__PDF_AUTH_TOKEN__} here: it can outlive the print route on SPA
 * navigation and would override a valid session token. The print page calls {@code setAccessToken}
 * explicitly; Playwright also seeds session/local storage before the bundle runs.
 */
function readStorage(): string | null {
  return readSessionStorageToken() ?? readLocalStorageToken();
}

function hydrateFromStorageOnce(): void {
  if (hydratedFromStorage) {
    return;
  }
  hydratedFromStorage = true;
  memoryToken = readStorage();
}

export function getAccessToken(): string | null {
  hydrateFromStorageOnce();
  return memoryToken;
}

export function setAccessToken(token: string): void {
  hydrateFromStorageOnce();
  memoryToken = token;
  try {
    sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
  } catch {
    // ignore quota / private mode
  }
}

export function clearAccessToken(): void {
  memoryToken = null;
  hydratedFromStorage = true;
  try {
    sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  } catch {
    // ignore
  }
  try {
    localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  } catch {
    // ignore
  }
}

export type TryRestoreSessionOptions = {
  /**
   * If true, always POST /api/auth/refresh (e.g. after HTTP 401 with an expired in-memory JWT).
   * If false/omitted, skip the network call when a non-empty access token is already cached.
   */
  force?: boolean;
};

/**
 * When access token is absent but refresh cookie may exist, obtain a new access token.
 * @returns true if an access token is now available
 */
export async function tryRestoreSession(options?: TryRestoreSessionOptions): Promise<boolean> {
  if (!options?.force && getAccessToken()) {
    return true;
  }
  const res = await fetch("/api/auth/refresh", {
    method: "POST",
    credentials: "include",
    headers: {
      "X-Tenant-ID": DEFAULT_WORKSPACE_TENANT_ID,
    },
  });
  if (!res.ok) {
    return false;
  }
  let body: unknown;
  try {
    body = await res.json();
  } catch {
    return false;
  }
  if (typeof body !== "object" || body === null) {
    return false;
  }
  const token = (body as Record<string, unknown>).accessToken;
  if (typeof token !== "string" || token.length === 0) {
    return false;
  }
  setAccessToken(token);
  return true;
}
