import {
  Alert,
  Box,
  Button,
  Container,
  CssBaseline,
  Snackbar,
  Stack,
  TextField,
  ThemeProvider,
  Typography,
  createTheme,
} from "@mui/material";
import { useEffect, useState } from "react";
import { Navigate, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { resetCsrfPrime } from "../api/apiFetch";
import { getAccessToken, setAccessToken } from "../auth/authSession";
import {
  CODE_LENGTH,
  RESEND_INTERVAL_SECONDS,
  describeLoginCodeError,
  normalizeCode,
  requestLoginCode,
  verifyLoginCode,
} from "../auth/loginCode";
import { toRefreshFailureReason } from "../types/auth";
import { getRefreshFailureMessage } from "../utils/authMessages";

const theme = createTheme();

type Step = "email" | "code";

export default function LoginPage(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const fromPath =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/";

  const [step, setStep] = useState<Step>("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
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
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Typography variant="h5" component="h1" gutterBottom>
          ログイン
        </Typography>
        {step === "email" ? (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              登録済みのメールアドレスを入力してください。ログイン用の6桁のコードをお送りします。
            </Typography>
            <Box component="form" onSubmit={(e) => void handleEmailSubmit(e)}>
              <Stack spacing={2}>
                {error !== null ? <Alert severity="error">{error}</Alert> : null}
                <TextField
                  label="メールアドレス"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(ev) => setEmail(ev.target.value)}
                  required
                  fullWidth
                  autoFocus
                />
                <Button type="submit" variant="contained" disabled={submitting} fullWidth>
                  {submitting ? "送信中…" : "コードを送る"}
                </Button>
              </Stack>
            </Box>
          </>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary">
              次のメールアドレスに6桁のコードを送りました。
            </Typography>
            <Typography variant="h6" component="p" sx={{ mb: 2, wordBreak: "break-all" }}>
              {email.trim()}
            </Typography>
            <Box component="form" onSubmit={(e) => void handleCodeSubmit(e)}>
              <Stack spacing={2}>
                {info !== null ? <Alert severity="info">{info}</Alert> : null}
                {error !== null ? <Alert severity="error">{error}</Alert> : null}
                <TextField
                  label="6桁のコード"
                  value={code}
                  onChange={(ev) => setCode(normalizeCode(ev.target.value))}
                  autoComplete="one-time-code"
                  inputProps={{ inputMode: "numeric", pattern: "[0-9]*" }}
                  required
                  fullWidth
                  autoFocus
                />
                <Typography variant="body2" color="text.secondary">
                  コードは10分で使えなくなります。届かない場合は、迷惑メールフォルダも確認してください。
                </Typography>
                <Button
                  type="submit"
                  variant="contained"
                  disabled={submitting || code.length !== CODE_LENGTH}
                  fullWidth
                >
                  {submitting ? "確認中…" : "ログイン"}
                </Button>
                <Stack direction="row" justifyContent="space-between" flexWrap="wrap" gap={1}>
                  <Button
                    variant="text"
                    onClick={() => void handleResend()}
                    disabled={submitting || resendSecondsLeft > 0}
                  >
                    {resendSecondsLeft > 0
                      ? `コードを再送する（あと${resendSecondsLeft}秒）`
                      : "コードを再送する"}
                  </Button>
                  <Button variant="text" onClick={backToEmail} disabled={submitting}>
                    メールアドレスを直す
                  </Button>
                </Stack>
              </Stack>
            </Box>
          </>
        )}
      </Container>
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
    </ThemeProvider>
  );
}
