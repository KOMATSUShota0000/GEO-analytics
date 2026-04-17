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
import { DEFAULT_WORKSPACE_TENANT_ID } from "../api/tenantConstants";
import { getAccessToken, setAccessToken } from "../auth/authSession";
import { toRefreshFailureReason } from "../types/auth";
import { getRefreshFailureMessage } from "../utils/authMessages";

const theme = createTheme();

export default function LoginPage(): JSX.Element {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const fromPath =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
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

  if (getAccessToken()) {
    return <Navigate to={fromPath} replace />;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const res = await fetch("/api/login", {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
          "X-Tenant-ID": DEFAULT_WORKSPACE_TENANT_ID,
        },
        body: JSON.stringify({ email: email.trim(), password }),
      });
      if (!res.ok) {
        const text = await res.text();
        setError(text.length > 0 ? text : `ログインに失敗しました (${res.status})`);
        return;
      }
      const body: unknown = await res.json();
      if (typeof body !== "object" || body === null) {
        setError("レスポンス形式が不正です");
        return;
      }
      const token = (body as Record<string, unknown>).accessToken;
      if (typeof token !== "string" || token.length === 0) {
        setError("アクセストークンがありません");
        return;
      }
      resetCsrfPrime();
      setAccessToken(token);
      navigate(fromPath, { replace: true });
    } catch {
      setError("通信エラーが発生しました");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Typography variant="h5" component="h1" gutterBottom>
          ログイン
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          開発環境の初期ユーザー: <code>bootstrap@example.com</code> / <code>bootstrap</code>
        </Typography>
        <Box component="form" onSubmit={(e) => void handleSubmit(e)}>
          <Stack spacing={2}>
            {error !== null ? <Alert severity="error">{error}</Alert> : null}
            <TextField
              label="メールアドレス"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(ev) => setEmail(ev.target.value)}
              required
              fullWidth
            />
            <TextField
              label="パスワード"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(ev) => setPassword(ev.target.value)}
              required
              fullWidth
            />
            <Button type="submit" variant="contained" disabled={submitting} fullWidth>
              {submitting ? "送信中…" : "ログイン"}
            </Button>
          </Stack>
        </Box>
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
