import { resetCsrfPrime } from "../api/apiFetch";
import { clearAccessToken } from "./authSession";

export function logout(): void {
  clearAccessToken();
  resetCsrfPrime();
}

export function clearClientState(): void {
  try {
    if (typeof window !== "undefined" && window.localStorage) {
      window.localStorage.clear();
    }
  } catch {
    // noop
  }
  try {
    if (typeof window !== "undefined" && window.sessionStorage) {
      window.sessionStorage.clear();
    }
  } catch {
    // noop
  }
}
