import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  FormControlLabel,
  Link,
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

const MAX_NOTIFICATION_EMAILS = 3;
const SLACK_CHANNEL_EMAIL_HELP_URL = "https://slack.com/intl/ja-jp/help/articles/206819278";
const LOAD_FAILED = "設定を読み込めませんでした。時間をおいて再度お試しください。";
const SAVE_FAILED = "保存できませんでした。時間をおいて再度お試しください。";
const EMAIL_RE =
  /^[\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/;

type ProjectSettings = {
  projectId: string;
  autoAuditEnabled: boolean;
  notificationEmails: string[];
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
    notificationEmails: Array.isArray(r.notificationEmails)
      ? r.notificationEmails.filter((v): v is string => typeof v === "string")
      : [],
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
  const [loaded, setLoaded] = useState(false);
  const [autoAudit, setAutoAudit] = useState(false);
  const [emails, setEmails] = useState<string[]>([]);
  const [draft, setDraft] = useState("");
  const [draftError, setDraftError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}/settings`);
      if (!res.ok) {
        throw new Error(res.status === 404 ? "このプロジェクトが見つかりませんでした。" : LOAD_FAILED);
      }
      const parsed = parseSettings(await responseJsonAsCamel(res));
      if (!parsed) throw new Error(LOAD_FAILED);
      setAutoAudit(parsed.autoAuditEnabled);
      setEmails(parsed.notificationEmails);
      setLoaded(true);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : LOAD_FAILED);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  // 入力欄に打ったまま「追加」を押さずに保存しても取りこぼさないよう、保存時にも同じ確認を通す。
  const withDraft = useCallback((): string[] | null => {
    const candidate = draft.trim();
    if (candidate.length === 0) return emails;
    if (!EMAIL_RE.test(candidate)) {
      setDraftError("メールアドレスの形式になっていません");
      return null;
    }
    if (emails.some((e) => e.toLowerCase() === candidate.toLowerCase())) {
      setDraftError("このアドレスはすでに登録されています");
      return null;
    }
    if (emails.length >= MAX_NOTIFICATION_EMAILS) {
      setDraftError(`登録できるのは${MAX_NOTIFICATION_EMAILS}件までです`);
      return null;
    }
    return [...emails, candidate];
  }, [draft, emails]);

  const addDraft = useCallback(() => {
    const next = withDraft();
    if (next === null) return;
    setEmails(next);
    setDraft("");
    setDraftError(null);
  }, [withDraft]);

  const removeEmail = useCallback((target: string) => {
    setEmails((prev) => prev.filter((e) => e !== target));
  }, []);

  const save = async () => {
    if (!projectId) return;
    const nextEmails = withDraft();
    if (nextEmails === null) return;
    setSaving(true);
    setError(null);
    try {
      const res = await apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          auto_audit_enabled: autoAudit,
          notification_emails: nextEmails,
        }),
      });
      if (!res.ok) {
        let msg = SAVE_FAILED;
        try {
          const o = parseJsonTextAsCamel(await res.text()) as { message?: unknown };
          if (res.status === 400 && typeof o.message === "string" && o.message.length > 0) msg = o.message;
        } catch {}
        throw new Error(msg);
      }
      setDraft("");
      setDraftError(null);
      setToast("設定を保存しました");
      void load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : SAVE_FAILED);
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
        ) : !loaded ? null : (
          <Stack spacing={3}>
            <Box sx={{ borderRadius: 2, border: 1, borderColor: "divider", p: 2 }}>
              <Typography variant="subtitle1" fontWeight={700} gutterBottom>
                定期監査と通知設定
              </Typography>
              <FormControlLabel
                control={<Switch checked={autoAudit} onChange={(_, v) => setAutoAudit(v)} color="primary" />}
                label="毎月1日2:00（日本時間）に自動監査を実行"
              />
              <Typography variant="subtitle2" fontWeight={700} sx={{ mt: 2 }}>
                通知先メールアドレス（{MAX_NOTIFICATION_EMAILS}件まで）
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                解析が終わると、登録したアドレスへお知らせが届きます。
              </Typography>
              {emails.length > 0 && (
                <Stack direction="row" useFlexGap flexWrap="wrap" spacing={1} sx={{ mt: 1.5 }}>
                  {emails.map((e) => (
                    <Chip key={e} label={e} onDelete={() => removeEmail(e)} />
                  ))}
                </Stack>
              )}
              <Stack direction="row" spacing={1} alignItems="flex-start" sx={{ mt: 1.5 }}>
                <TextField
                  label="メールアドレスを追加"
                  value={draft}
                  onChange={(e) => {
                    setDraft(e.target.value);
                    setDraftError(null);
                  }}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.nativeEvent.isComposing) {
                      e.preventDefault();
                      addDraft();
                    }
                  }}
                  fullWidth
                  size="small"
                  type="email"
                  autoComplete="email"
                  disabled={emails.length >= MAX_NOTIFICATION_EMAILS}
                  error={Boolean(draftError)}
                  helperText={draftError ?? (emails.length >= MAX_NOTIFICATION_EMAILS ? `${MAX_NOTIFICATION_EMAILS}件まで登録済みです。変えるときは不要なアドレスを消してください。` : " ")}
                />
                <Button
                  variant="outlined"
                  onClick={addDraft}
                  disabled={emails.length >= MAX_NOTIFICATION_EMAILS || draft.trim().length === 0}
                  sx={{ flexShrink: 0, mt: 0.25 }}
                >
                  追加
                </Button>
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                Slackで受け取りたい場合は、チャンネルのメールアドレスを追加してください（Slackの有料プランで発行できます）。{" "}
                <Link href={SLACK_CHANNEL_EMAIL_HELP_URL} target="_blank" rel="noreferrer">
                  発行のしかた
                </Link>
              </Typography>
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
