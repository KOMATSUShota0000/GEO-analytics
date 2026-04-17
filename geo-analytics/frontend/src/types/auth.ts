export type RefreshFailureReason =
  | "token_expired"
  | "session_revoked"
  | "account_disabled"
  | "credentials_revoked"
  | "tenant_suspended"
  | "maintenance"
  | "version_mismatch"
  | "network_error"
  | "unknown";

export type RefreshResult =
  | { success: true }
  | { success: false; reason: RefreshFailureReason };

const KNOWN_REASONS: ReadonlySet<string> = new Set<RefreshFailureReason>([
  "token_expired",
  "session_revoked",
  "account_disabled",
  "credentials_revoked",
  "tenant_suspended",
  "maintenance",
  "version_mismatch",
  "network_error",
  "unknown",
]);

export function toRefreshFailureReason(value: unknown): RefreshFailureReason {
  if (typeof value === "string" && KNOWN_REASONS.has(value)) {
    return value as RefreshFailureReason;
  }
  return "unknown";
}
