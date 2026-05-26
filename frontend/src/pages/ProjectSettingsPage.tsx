import {
  Alert,
  Box,
  Button,
  Container,
  FormControlLabel,
  Snackbar,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { ArrowLeft, PlusCircle } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { apiFetch, parseJsonTextAsCamel, responseJsonAsCamel } from "../api/apiFetch";

const SLACK_PREFIX = "https://hooks.slack.com/";
const EMAIL_RE =
  /^[\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/;

type ProjectSettings = {
  projectId: string;
  autoAuditEnabled: boolean;
  slackWebhookUrl: string | null;
  notificationEmail: string | null;
  lastAuditAt: string | null;
};

function parseSettings(raw: unknown): ProjectSettings | null {
  if (raw === null || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;
  if (typeof r.projectId !== "string") return null;
  if (typeof r.autoAuditEnabled !== "boolean") return null;
  return {
    projectId: r.projectId,
    autoAuditEnabled: r.autoAuditEnabled,
    slackWebhookUrl:
      r.slackWebhookUrl === undefined || r.slackWebhookUrl === null
        ? null
        : typeof r.slackWebhookUrl === "string"
          ? r.slackWebhookUrl
          : null,
    notificationEmail:
      r.notificationEmail === undefined || r.notificationEmail === null
        ? null
        : typeof r.notificationEmail === "string"
          ? r.notificationEmail
          : null,
    lastAuditAt:
      r.lastAuditAt === undefined || r.lastAuditAt === null
        ? null
        : typeof r.lastAuditAt === "string"
          ? r.lastAuditAt
          : null,
  };
}

export default function ProjectSettingsPage(): JSX.Element {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnJobId = searchParams.get("returnJob")?.trim() ?? "";
  const goBackToAnalysis = useCallback(() => {
    if (returnJobId.length > 0) {
      navigate(`/job/${returnJobId}`);
    } else {
      navigate(-1);
    }
  }, [navigate, returnJobId]);
  const goToNewJob = useCallback(() => {
    navigate("/");
  }, [navigate]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [autoAudit, setAutoAudit] = useState(false);
  const [slackUrl, setSlackUrl] = useState("");
  const [email, setEmail] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ slack?: string; email?: string }>({});

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}/settings`);
      if (!res.ok) {
        throw new Error(await res.text());
      }
      const parsed = parseSettings(await responseJsonAsCamel(res));
      if (!parsed) throw new Error("設定の形式が不正です");
      setAutoAudit(parsed.autoAuditEnabled);
      setSlackUrl(parsed.slackWebhookUrl ?? "");
      setEmail(parsed.notificationEmail ?? "");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  const validate = useCallback((): boolean => {
    const next: { slack?: string; email?: string } = {};
    const s = slackUrl.trim();
    if (s.length > 0 && !s.startsWith(SLACK_PREFIX)) {
      next.slack = `Slack Webhookは ${SLACK_PREFIX} で始まる必要があります`;
    }
    const em = email.trim();
    if (em.length > 0 && !EMAIL_RE.test(em)) {
      next.email = "メール形式が正しくありません";
    }
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  }, [slackUrl, email]);

  const save = async () => {
    if (!projectId || !validate()) return;
    setSaving(true);
    setError(null);
    try {
      const res = await apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          auto_audit_enabled: autoAudit,
          slack_webhook_url: slackUrl.trim(),
          notification_email: email.trim(),
        }),
      });
      if (!res.ok) {
        const t = await res.text();
        let msg = t || `HTTP ${res.status}`;
        try {
          const o = parseJsonTextAsCamel(t) as { detail?: string };
          if (typeof o.detail === "string") msg = o.detail;
        } catch {}
        throw new Error(msg);
      }
      setToast("設定を保存しました");
      void load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <nav
          className="mb-5 flex flex-wrap items-center gap-x-1 gap-y-2 border-b border-slate-200/90 pb-4"
          aria-label="ページ導線"
        >
          <button
            type="button"
            onClick={() => goBackToAnalysis()}
            className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm font-medium text-indigo-600 transition hover:bg-indigo-50 hover:text-indigo-800"
          >
            <ArrowLeft className="h-4 w-4 shrink-0" strokeWidth={2.25} aria-hidden />
            解析結果に戻る
          </button>
          <span className="mx-1 hidden h-4 w-px bg-slate-200 sm:inline-block" aria-hidden />
          <button
            type="button"
            onClick={() => goToNewJob()}
            className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm font-medium text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
          >
            <PlusCircle className="h-4 w-4 shrink-0 text-indigo-500" strokeWidth={2} aria-hidden />
            新規ジョブ作成
          </button>
        </nav>
        <Typography variant="h4" component="h1" fontWeight={600} gutterBottom>
          プロジェクト設定
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          定期監査と通知チャネルを管理します。
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        {loading ? (
          <Typography color="text.secondary">読み込み中…</Typography>
        ) : (
          <Stack spacing={3}>
            <Box sx={{ borderRadius: 2, border: 1, borderColor: "divider", p: 2 }}>
              <Typography variant="subtitle1" fontWeight={700} gutterBottom>
                定期監査と通知設定
              </Typography>
              <FormControlLabel
                control={<Switch checked={autoAudit} onChange={(_, v) => setAutoAudit(v)} color="primary" />}
                label="毎月1日2:00（日本時間）に自動監査を実行"
              />
              <TextField
                label="Slack Incoming Webhook URL"
                value={slackUrl}
                onChange={(e) => setSlackUrl(e.target.value)}
                fullWidth
                margin="normal"
                placeholder="https://hooks.slack.com/services/..."
                error={Boolean(fieldErrors.slack)}
                helperText={fieldErrors.slack ?? "空欄でSlack通知を無効化"}
              />
              <TextField
                label="通知メールアドレス"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                fullWidth
                margin="normal"
                type="email"
                autoComplete="email"
                error={Boolean(fieldErrors.email)}
                helperText={fieldErrors.email ?? "SMTP設定済みの環境でのみ送信されます"}
              />
              <Button variant="contained" size="large" onClick={() => void save()} disabled={saving} sx={{ mt: 2 }}>
                {saving ? "保存中…" : "設定を保存"}
              </Button>
            </Box>
          </Stack>
        )}
        <Snackbar
          open={toast !== null}
          autoHideDuration={4000}
          onClose={() => setToast(null)}
          anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        >
          <Alert severity="success" variant="filled" onClose={() => setToast(null)}>
            {toast}
          </Alert>
        </Snackbar>
      </Container>
    </>
  );
}
