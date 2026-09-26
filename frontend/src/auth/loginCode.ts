/** メールに届くコードでのログイン（#144）の通信とエラーの読み取り。画面は LoginPage。 */

export const RESEND_INTERVAL_SECONDS = 60;
export const CODE_LENGTH = 6;

export type LoginCodeError =
  | { kind: "resend_too_soon"; retryAfterSeconds: number }
  | { kind: "send_limit"; retryAfterSeconds: number }
  | { kind: "rejected" }
  | { kind: "invalid_input" }
  | { kind: "network" }
  | { kind: "server"; status: number };

export type LoginCodeResult<T> = { ok: true; value: T } | { ok: false; error: LoginCodeError };

export async function requestLoginCode(email: string): Promise<LoginCodeResult<void>> {
  const res = await post("/api/auth/code", { email });
  if (!res.ok) return res;
  if (res.value.status === 202) return { ok: true, value: undefined };
  return { ok: false, error: await toError(res.value) };
}

/** 成功したらアクセストークンを返す。リフレッシュトークンはサーバーが HttpOnly クッキーで渡す。 */
export async function verifyLoginCode(email: string, code: string): Promise<LoginCodeResult<string>> {
  const res = await post("/api/auth/code/verify", { email, code });
  if (!res.ok) return res;
  if (!res.value.ok) return { ok: false, error: await toError(res.value) };
  const body: unknown = await res.value.json().catch(() => null);
  const token =
    typeof body === "object" && body !== null ? (body as Record<string, unknown>).accessToken : undefined;
  if (typeof token !== "string" || token.length === 0) {
    return { ok: false, error: { kind: "server", status: res.value.status } };
  }
  return { ok: true, value: token };
}

/**
 * 入力されたコードを6桁の半角数字に整える。
 * Why: 日本語入力のままだと全角数字になり、メールから貼り付けると空白や前後の文字が交ざるため。
 */
export function normalizeCode(input: string): string {
  let digits = "";
  for (const ch of input) {
    const c = ch.charCodeAt(0);
    if (c >= 0xff10 && c <= 0xff19) {
      digits += String.fromCharCode(c - 0xff10 + 0x30);
    } else if (c >= 0x30 && c <= 0x39) {
      digits += ch;
    }
    if (digits.length === CODE_LENGTH) break;
  }
  return digits;
}

export function describeLoginCodeError(error: LoginCodeError, step: "email" | "code"): string {
  switch (error.kind) {
    case "resend_too_soon":
      return `続けて送ることはできません。あと${error.retryAfterSeconds}秒待ってから、もう一度お試しください。`;
    case "send_limit":
      return `送信回数の上限に達しました。${formatWait(error.retryAfterSeconds)}ほど待ってから、もう一度お試しください。`;
    case "rejected":
      return "コードが正しくないか、期限が切れています。うまくいかない場合は、コードを再送してやり直してください。";
    case "invalid_input":
      return step === "email" ? "メールアドレスの形式を確認してください。" : "6桁の数字を入力してください。";
    case "network":
      return "通信エラーが発生しました。接続を確認して、もう一度お試しください。";
    case "server":
      return `エラーが発生しました（${error.status}）。時間をおいて、もう一度お試しください。`;
    default: {
      const _exhaustive: never = error;
      void _exhaustive;
      return "エラーが発生しました。時間をおいて、もう一度お試しください。";
    }
  }
}

export function formatWait(seconds: number): string {
  if (seconds < 60) return `${Math.max(1, Math.ceil(seconds))}秒`;
  const totalMinutes = Math.ceil(seconds / 60);
  if (totalMinutes < 60) return `${totalMinutes}分`;
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes === 0 ? `${hours}時間` : `${hours}時間${minutes}分`;
}

async function post(url: string, body: unknown): Promise<LoginCodeResult<Response>> {
  try {
    const res = await fetch(url, {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return { ok: true, value: res };
  } catch {
    return { ok: false, error: { kind: "network" } };
  }
}

async function toError(res: Response): Promise<LoginCodeError> {
  if (res.status === 400) return { kind: "invalid_input" };
  if (res.status === 401) return { kind: "rejected" };
  if (res.status === 429) {
    const body: unknown = await res.json().catch(() => null);
    const record = typeof body === "object" && body !== null ? (body as Record<string, unknown>) : {};
    const details =
      typeof record.details === "object" && record.details !== null
        ? (record.details as Record<string, unknown>)
        : {};
    const fromBody = Number(details.retry_after_seconds);
    const fromHeader = Number(res.headers.get("Retry-After"));
    const retryAfterSeconds = Number.isFinite(fromBody) && fromBody > 0
      ? fromBody
      : Number.isFinite(fromHeader) && fromHeader > 0
        ? fromHeader
        : RESEND_INTERVAL_SECONDS;
    return record.error_code === "login_code_resend_too_soon"
      ? { kind: "resend_too_soon", retryAfterSeconds }
      : { kind: "send_limit", retryAfterSeconds };
  }
  return { kind: "server", status: res.status };
}
