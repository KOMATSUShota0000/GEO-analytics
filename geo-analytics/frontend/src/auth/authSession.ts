import { DEFAULT_WORKSPACE_TENANT_ID } from "../api/tenantConstants";

const STORAGE_KEY = "geo_analytics.access_token";

let memoryToken: string | null = null;
let hydratedFromStorage = false;

function readStorage(): string | null {
  if (typeof sessionStorage === "undefined") {
    return null;
  }
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw !== null && raw.length > 0 ? raw : null;
  } catch {
    return null;
  }
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
    sessionStorage.setItem(STORAGE_KEY, token);
  } catch {
    // ignore quota / private mode
  }
}

export function clearAccessToken(): void {
  memoryToken = null;
  hydratedFromStorage = true;
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
}

/**
 * When access token is absent but refresh cookie may exist, obtain a new access token.
 * @returns true if an access token is now available
 */
export async function tryRestoreSession(): Promise<boolean> {
  if (getAccessToken()) {
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
