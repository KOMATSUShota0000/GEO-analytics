import type { RefreshFailureReason } from "../types/auth";

export function getRefreshFailureMessage(reason: RefreshFailureReason): string {
  switch (reason) {
    case "token_expired":
      return "セッションの有効期限が切れました。再度ログインしてください。";
    case "session_revoked":
      return "セキュリティ保護のため、別端末でのログインを検知しログアウトしました。再度ログインしてください。";
    case "account_disabled":
      return "アカウントが無効化されました。管理者にお問い合わせください。";
    case "credentials_revoked":
      return "パスワードが変更されたため、再ログインが必要です。";
    case "tenant_suspended":
      return "ご利用中の組織が一時停止されています。管理者にお問い合わせください。";
    case "maintenance":
      return "現在メンテナンス中です。しばらくしてから再度お試しください。";
    case "version_mismatch":
      return "アプリの更新が必要です。ページを再読み込みしてください。";
    case "network_error":
      return "ネットワーク接続を確認してください。";
    case "unknown":
      return "セッションに問題が発生しました。再度ログインしてください。";
    default: {
      const _exhaustive: never = reason;
      void _exhaustive;
      return "セッションに問題が発生しました。再度ログインしてください。";
    }
  }
}
