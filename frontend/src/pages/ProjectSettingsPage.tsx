import { Alert, Snackbar } from "@mui/material";
import {
  ArrowLeft,
  Bell,
  CalendarClock,
  ExternalLink,
  Eye,
  Mail,
  MessageSquare,
  PlusCircle,
  X,
} from "lucide-react";
import { useCallback, useEffect, useId, useState, type ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { apiFetch, parseJsonTextAsCamel, responseJsonAsCamel } from "../api/apiFetch";

const MAX_NOTIFICATION_EMAILS = 3;
const SLACK_CHANNEL_EMAIL_HELP_URL = "https://slack.com/intl/ja-jp/help/articles/206819278";
const LOAD_FAILED = "設定を読み込めませんでした。時間をおいて再度お試しください。";
const SAVE_FAILED = "保存できませんでした。時間をおいて再度お試しください。";
const EMAIL_RE =
  /^[\w.!#$%&'*+/=?^`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$/;
const SLACK_ADDRESS_RE = /@([A-Za-z0-9-]+\.)*slack\.com$/i;

// 届くメール（NotificationService.sendEmail）の見本。メールの文面を変えたら、ここも合わせて直す（#183）。
const SAMPLE_TOP_CHANGES = [
  "[質問文1] — 今回 55.0 / 前回 40.0 / Δ15.0",
  "[質問文2] — 今回 31.2 / 前回 36.0 / Δ-4.8",
  "[質問文3] — 今回 48.0 / 前回 45.5 / Δ2.5",
];

type ProjectSettings = {
  projectId: string;
  projectName: string;
  targetUrl: string;
  autoAuditEnabled: boolean;
  notificationEmails: string[];
};

function parseSettings(raw: unknown): ProjectSettings | null {
  if (raw === null || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;
  if (typeof r.projectId !== "string") return null;
  if (typeof r.autoAuditEnabled !== "boolean") return null;
  return {
    projectId: r.projectId,
    projectName: typeof r.projectName === "string" ? r.projectName : "",
    targetUrl: typeof r.targetUrl === "string" ? r.targetUrl : "",
    autoAuditEnabled: r.autoAuditEnabled,
    notificationEmails: Array.isArray(r.notificationEmails)
      ? r.notificationEmails.filter((v): v is string => typeof v === "string")
      : [],
  };
}

function SettingsCard({
  icon,
  title,
  description,
  children,
}: {
  icon: ReactNode;
  title: string;
  description?: string;
  children: ReactNode;
}): JSX.Element {
  return (
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-center gap-3 border-b border-slate-100 bg-[#fcfbff] px-4 py-3.5 sm:px-6 sm:py-4">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-50 text-indigo-600">
          {icon}
        </div>
        <div className="flex flex-col gap-0.5">
          <h2 className="text-[15px] font-semibold text-slate-900">{title}</h2>
          {description ? <p className="text-[13px] leading-relaxed text-slate-600">{description}</p> : null}
        </div>
      </div>
      <div className="px-4 py-4 sm:px-6 sm:pb-6 sm:pt-5">{children}</div>
    </section>
  );
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
  const draftInputId = useId();
  const draftHelpId = useId();
  const autoAuditLabelId = useId();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [projectName, setProjectName] = useState("");
  const [targetUrl, setTargetUrl] = useState("");
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
      setProjectName(parsed.projectName);
      setTargetUrl(parsed.targetUrl);
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

  const full = emails.length >= MAX_NOTIFICATION_EMAILS;
  const draftHelp =
    draftError ?? (full ? `${MAX_NOTIFICATION_EMAILS}件まで登録済みです。変えるときは不要なアドレスを消してください。` : null);

  return (
    <div className="min-h-screen bg-gradient-to-b from-[#f5f2fb] via-[#eae3f4] to-[#f2eef9]">
      <div className="mx-auto max-w-5xl px-4 py-6 text-slate-900 sm:px-6 sm:py-8">
        <nav
          className="mb-6 flex flex-wrap items-center gap-x-1 gap-y-2 border-b border-slate-200/90 pb-4"
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
        <div className="mb-6 flex flex-col gap-2">
          <h1 className="text-2xl font-semibold tracking-tight text-slate-900">プロジェクト設定</h1>
          {loaded && (projectName || targetUrl) ? (
            <div className="flex flex-col gap-1 text-sm sm:flex-row sm:items-center sm:gap-2.5">
              {projectName ? <span className="font-semibold text-slate-700">{projectName}</span> : null}
              {projectName && targetUrl ? (
                <span className="hidden text-slate-300 sm:inline" aria-hidden>
                  |
                </span>
              ) : null}
              {targetUrl ? <span className="break-all text-slate-500">{targetUrl}</span> : null}
            </div>
          ) : null}
        </div>
        {error ? (
          <div
            role="alert"
            className="mb-6 flex items-start justify-between gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
          >
            <span>{error}</span>
            <button
              type="button"
              onClick={() => setError(null)}
              aria-label="閉じる"
              className="-m-1 rounded-md p-1 text-rose-700 hover:bg-rose-100"
            >
              <X className="h-4 w-4" aria-hidden />
            </button>
          </div>
        ) : null}
        {loading ? (
          <p className="text-sm text-slate-500">読み込み中…</p>
        ) : !loaded ? null : (
          <div className="grid items-start gap-6 lg:grid-cols-12">
            <div className="flex flex-col gap-5 lg:col-span-7">
              <SettingsCard
                icon={<Bell className="h-[18px] w-[18px]" aria-hidden />}
                title="解析完了のお知らせ"
                description="解析が終わると、登録したアドレスへお知らせが届きます。"
              >
                <div className="flex flex-col gap-3">
                  <div className="flex items-baseline justify-between">
                    <label htmlFor={draftInputId} className="text-sm font-semibold text-slate-700">
                      通知先メールアドレス
                    </label>
                    <span className="text-[13px] text-slate-500">
                      {emails.length} / {MAX_NOTIFICATION_EMAILS}件
                    </span>
                  </div>
                  {emails.length === 0 ? (
                    <p className="rounded-lg border border-dashed border-slate-300 px-3 py-3 text-[13px] text-slate-500">
                      まだ登録されていません。お知らせを受け取るアドレスを追加してください。
                    </p>
                  ) : (
                    <ul className="flex flex-col gap-2">
                      {emails.map((e) => (
                        <li
                          key={e}
                          className="flex items-center gap-2.5 rounded-lg border border-slate-200 bg-slate-50 py-1 pl-3 pr-1"
                        >
                          {SLACK_ADDRESS_RE.test(e) ? (
                            <MessageSquare className="h-4 w-4 shrink-0 text-slate-500" aria-hidden />
                          ) : (
                            <Mail className="h-4 w-4 shrink-0 text-slate-500" aria-hidden />
                          )}
                          <span className="min-w-0 flex-1 break-all text-sm text-slate-900">{e}</span>
                          <button
                            type="button"
                            onClick={() => removeEmail(e)}
                            aria-label={`${e} を削除`}
                            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md text-slate-500 hover:bg-slate-200 hover:text-slate-700"
                          >
                            <X className="h-4 w-4" aria-hidden />
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <input
                      id={draftInputId}
                      type="email"
                      autoComplete="email"
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
                      disabled={full}
                      placeholder="メールアドレスを追加（例: name@example.co.jp）"
                      aria-invalid={draftError ? true : undefined}
                      aria-describedby={draftHelp ? draftHelpId : undefined}
                      className={`h-11 min-w-0 flex-1 rounded-lg border bg-white px-3.5 text-base text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-1 disabled:bg-slate-100 disabled:text-slate-400 sm:text-sm ${
                        draftError
                          ? "border-rose-400 focus:border-rose-500 focus:ring-rose-500"
                          : "border-slate-300 focus:border-indigo-500 focus:ring-indigo-500"
                      }`}
                    />
                    <button
                      type="button"
                      onClick={addDraft}
                      disabled={full || draft.trim().length === 0}
                      className="h-11 shrink-0 rounded-lg border border-indigo-600 bg-white px-5 text-sm font-semibold text-indigo-600 transition hover:bg-indigo-50 disabled:cursor-not-allowed disabled:border-slate-300 disabled:text-slate-400 disabled:hover:bg-white"
                    >
                      追加
                    </button>
                  </div>
                  {draftHelp ? (
                    <p id={draftHelpId} className={`text-[13px] ${draftError ? "text-rose-600" : "text-slate-500"}`}>
                      {draftHelp}
                    </p>
                  ) : null}
                  <div className="flex gap-3 rounded-lg bg-indigo-50 px-4 py-3.5 text-[13px] leading-relaxed text-indigo-950">
                    <MessageSquare className="mt-0.5 h-[18px] w-[18px] shrink-0 text-indigo-700" aria-hidden />
                    <div className="flex flex-col gap-1">
                      <span className="font-semibold">Slackで受け取るには</span>
                      <span>
                        チャンネルの設定にある「このチャンネルにメールを送信」でアドレスを発行し、上の欄に追加してください（Slackの有料プランで使えます）。
                      </span>
                      <a
                        href={SLACK_CHANNEL_EMAIL_HELP_URL}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex w-fit items-center gap-1 py-1 font-semibold text-indigo-700 hover:text-indigo-900"
                      >
                        発行のしかた
                        <ExternalLink className="h-3.5 w-3.5" aria-hidden />
                      </a>
                    </div>
                  </div>
                </div>
              </SettingsCard>
              <SettingsCard icon={<CalendarClock className="h-[18px] w-[18px]" aria-hidden />} title="定期監査">
                <div className="flex min-h-11 items-center gap-3">
                  <button
                    type="button"
                    role="switch"
                    aria-checked={autoAudit}
                    aria-labelledby={autoAuditLabelId}
                    onClick={() => setAutoAudit((v) => !v)}
                    className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500 ${
                      autoAudit ? "bg-indigo-600" : "bg-slate-300"
                    }`}
                  >
                    <span
                      className={`inline-block h-5 w-5 rounded-full bg-white shadow transition ${
                        autoAudit ? "translate-x-[22px]" : "translate-x-0.5"
                      }`}
                    />
                  </button>
                  <span id={autoAuditLabelId} className="text-sm leading-relaxed text-slate-700">
                    毎月1日2:00（日本時間）に自動監査を実行
                  </span>
                </div>
              </SettingsCard>
              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={() => void save()}
                  disabled={saving}
                  className="h-11 w-full rounded-lg bg-indigo-600 px-7 text-[15px] font-semibold text-white shadow-sm transition hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
                >
                  {saving ? "保存中…" : "設定を保存"}
                </button>
              </div>
            </div>
            <aside className="lg:col-span-5">
              <SettingsCard
                icon={<Eye className="h-[18px] w-[18px]" aria-hidden />}
                title="届くメールの見本"
                description="数値と質問文は見本です"
              >
                <div className="flex flex-col gap-3.5 text-[13px] leading-relaxed text-slate-700">
                  <div className="flex flex-col gap-1 border-b border-dashed border-slate-200 pb-3">
                    <span className="text-slate-500">件名</span>
                    <span className="break-all font-semibold text-slate-900">
                      [GEOアナリティクス] {projectName || "[プロジェクト名]"} の解析が完了しました
                    </span>
                  </div>
                  <p className="break-all text-slate-900">{projectName || "[プロジェクト名]"} の解析が完了しました。</p>
                  <div className="flex flex-col gap-1">
                    <span className="font-semibold text-slate-900">■ AIの回答に取り上げられた割合（SoMスコア）の平均</span>
                    <span>今回 42.3（前回 38.1 から +4.2）</span>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <span className="font-semibold text-slate-900">■ 前回から大きく動いた質問</span>
                    <ol className="flex list-decimal flex-col gap-1 pl-5">
                      {SAMPLE_TOP_CHANGES.map((line) => (
                        <li key={line}>{line}</li>
                      ))}
                    </ol>
                    <span className="text-slate-500">※ 前回と同じ質問は3件でした。今回はじめて測った質問が2件あります。</span>
                  </div>
                  <span className="font-semibold text-indigo-700">▶ 解析結果を見る</span>
                  <span className="text-xs text-slate-500">
                    このメールは、プロジェクト設定で登録された宛先にお送りしています。
                  </span>
                </div>
              </SettingsCard>
            </aside>
          </div>
        )}
      </div>
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
    </div>
  );
}
