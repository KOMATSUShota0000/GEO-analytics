import { Alert, Snackbar } from "@mui/material";
import { useEffect, useState } from "react";
import { Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { resetCsrfPrime } from "../api/apiFetch";
import { getAccessToken, setAccessToken } from "../auth/authSession";
import {
  CODE_LENGTH,
  RESEND_INTERVAL_SECONDS,
  describeLoginCodeError,
  requestLoginCode,
  verifyLoginCode,
} from "../auth/loginCode";
import CodeInput from "../components/auth/CodeInput";
import LoginBrandPanel, { BrandMark, PRODUCT_NAME } from "../components/auth/LoginBrandPanel";
import { toRefreshFailureReason } from "../types/auth";
import { getRefreshFailureMessage } from "../utils/authMessages";

type Step = "email" | "code";

const PRIMARY_BUTTON =
  "flex w-full items-center justify-center gap-2 rounded-lg bg-indigo-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none";
const TEXT_BUTTON =
  "rounded-md px-1 py-1 text-sm font-medium text-indigo-600 transition hover:text-indigo-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 disabled:cursor-not-allowed disabled:text-slate-400";

export default function LoginPage(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const fromPath =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/";

  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [codeInputKey, setCodeInputKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resendAvailableAt, setResendAvailableAt] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  useEffect(() => {
    const rawReason = searchParams.get("reason");
    if (rawReason === null) {
      return;
    }
    const reason = toRefreshFailureReason(rawReason);
    setToastMessage(getRefreshFailureMessage(reason));
    navigate(location.pathname, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (resendAvailableAt === null) {
      return;
    }
    const timer = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= resendAvailableAt) {
        window.clearInterval(timer);
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [resendAvailableAt]);

  if (getAccessToken()) {
    return <Navigate to={fromPath} replace />;
  }

  const resendSecondsLeft =
    resendAvailableAt === null ? 0 : Math.max(0, Math.ceil((resendAvailableAt - now) / 1000));

  const startResendWait = (seconds: number) => {
    const current = Date.now();
    setNow(current);
    setResendAvailableAt(current + seconds * 1000);
  };

  const sendCode = async (): Promise<boolean> => {
    setError(null);
    setInfo(null);
    setSubmitting(true);
    try {
      const result = await requestLoginCode(email.trim());
      if (!result.ok) {
        if (result.error.kind === "resend_too_soon") {
          startResendWait(result.error.retryAfterSeconds);
        }
        setError(describeLoginCodeError(result.error, "email"));
        return false;
      }
      startResendWait(RESEND_INTERVAL_SECONDS);
      return true;
    } finally {
      setSubmitting(false);
    }
  };

  const handleEmailSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (await sendCode()) {
      setCode("");
      setStep("code");
    }
  };

  const handleResend = async () => {
    setCode("");
    if (await sendCode()) {
      setInfo("新しいコードを送りました。前に届いたコードは使えなくなります。");
      setCodeInputKey((k) => k + 1);
    }
  };

  const handleCodeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setInfo(null);
    setSubmitting(true);
    try {
      const result = await verifyLoginCode(email.trim(), code);
      if (!result.ok) {
        setError(describeLoginCodeError(result.error, "code"));
        return;
      }
      resetCsrfPrime();
      setAccessToken(result.value);
      navigate(fromPath, { replace: true });
    } finally {
      setSubmitting(false);
    }
  };

  const backToEmail = () => {
    setStep("email");
    setCode("");
    setError(null);
    setInfo(null);
  };

  return (
    <div className="min-h-screen bg-white lg:grid lg:grid-cols-2">
      <LoginBrandPanel />
      <main className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12 sm:px-6 lg:bg-white">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <BrandMark />
            <span className="text-lg font-semibold text-slate-900">{PRODUCT_NAME}</span>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8 lg:border-0 lg:p-0 lg:shadow-none">
            <h1 className="text-2xl font-bold text-slate-900">ログイン</h1>
            <StepIndicator step={step} />

            {step === "email" ? (
              <form className="mt-6 space-y-5" onSubmit={(e) => void handleEmailSubmit(e)}>
                <p className="text-sm leading-relaxed text-slate-500">
                  登録済みのメールアドレスを入力してください。ログイン用の6桁のコードをお送りします。
                </p>
                {error !== null ? <Notice tone="error">{error}</Notice> : null}
                <div className="space-y-1.5">
                  <label htmlFor="login-email" className="block text-sm font-semibold text-slate-700">
                    メールアドレス
                  </label>
                  <input
                    id="login-email"
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(ev) => setEmail(ev.target.value)}
                    required
                    autoFocus
                    placeholder="name@example.com"
                    className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                </div>
                <button type="submit" disabled={submitting} className={PRIMARY_BUTTON}>
                  {submitting ? <Spinner /> : null}
                  {submitting ? "送信中…" : "コードを送る"}
                </button>
              </form>
            ) : (
              <form className="mt-6 space-y-5" onSubmit={(e) => void handleCodeSubmit(e)}>
                <div>
                  <p className="text-sm text-slate-500">次のメールアドレスに6桁のコードを送りました。</p>
                  <p className="mt-1 break-all text-base font-semibold text-slate-900">{email.trim()}</p>
                </div>
                {info !== null ? <Notice tone="info">{info}</Notice> : null}
                {error !== null ? <Notice tone="error">{error}</Notice> : null}
                <CodeInput
                  key={codeInputKey}
                  value={code}
                  onChange={setCode}
                  disabled={submitting}
                  invalid={error !== null}
                  autoFocus
                />
                <p className="text-xs leading-relaxed text-slate-500">
                  コードは10分で使えなくなります。届かない場合は、迷惑メールフォルダも確認してください。
                </p>
                <button
                  type="submit"
                  disabled={submitting || code.length !== CODE_LENGTH}
                  className={PRIMARY_BUTTON}
                >
                  {submitting ? <Spinner /> : null}
                  {submitting ? "確認中…" : "ログイン"}
                </button>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <button
                    type="button"
                    onClick={() => void handleResend()}
                    disabled={submitting || resendSecondsLeft > 0}
                    className={TEXT_BUTTON}
                  >
                    {resendSecondsLeft > 0 ? `コードを再送する（あと${resendSecondsLeft}秒）` : "コードを再送する"}
                  </button>
                  <button type="button" onClick={backToEmail} disabled={submitting} className={TEXT_BUTTON}>
                    メールアドレスを直す
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      </main>
      <Snackbar
        open={toastMessage !== null}
        autoHideDuration={6000}
        onClose={() => setToastMessage(null)}
        anchorOrigin={{ vertical: "top", horizontal: "center" }}
      >
        <Alert
          severity="warning"
          variant="filled"
          onClose={() => setToastMessage(null)}
          sx={{ width: "100%" }}
        >
          {toastMessage}
        </Alert>
      </Snackbar>
    </div>
  );
}

function StepIndicator({ step }: { step: Step }) {
  const items: { key: Step; label: string }[] = [
    { key: "email", label: "メールアドレス" },
    { key: "code", label: "コード" },
  ];
  const currentIndex = items.findIndex((item) => item.key === step);
  return (
    <ol className="mt-3 flex items-center gap-2 text-xs font-medium" aria-label="ログインの手順">
      {items.map((item, index) => {
        const done = index < currentIndex;
        const current = index === currentIndex;
        return (
          <li key={item.key} className="flex items-center gap-2" aria-current={current ? "step" : undefined}>
            {index > 0 ? <span aria-hidden className="h-px w-6 bg-slate-200" /> : null}
            <span
              className={[
                "flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-semibold",
                current || done ? "bg-indigo-600 text-white" : "bg-slate-200 text-slate-500",
              ].join(" ")}
            >
              {done ? "✓" : index + 1}
            </span>
            <span className={current ? "text-indigo-700" : done ? "text-slate-600" : "text-slate-400"}>
              {item.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function Notice({ tone, children }: { tone: "error" | "info"; children: string }) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={[
        "rounded-lg border px-3 py-2.5 text-sm leading-relaxed",
        tone === "error"
          ? "border-red-200 bg-red-50 text-red-700"
          : "border-indigo-100 bg-indigo-50 text-indigo-700",
      ].join(" ")}
    >
      {children}
    </div>
  );
}

function Spinner() {
  return (
    <span
      aria-hidden
      className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"
    />
  );
}
