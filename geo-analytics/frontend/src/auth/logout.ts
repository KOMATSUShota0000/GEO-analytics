import { resetCsrfPrime } from "../api/apiFetch";
import { clearAccessToken } from "./authSession";

export function logout(): void {
  clearAccessToken();
  resetCsrfPrime();
}
