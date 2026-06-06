import { apiFetch, responseJsonAsCamel } from "../api/apiFetch";
import { downloadJobPdfWithAuth } from "../api/downloadJobPdf";
import { fetchWorkspacePlan, type WorkspaceSubscriptionPlan } from "../api/workspace-api";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link as RouterLink, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { buildEmotionalAlerts } from "../lib/buildEmotionalAlerts";
import { mergeBannerJobHint } from "../lib/bannerJobHint";
import { AnalysisCharts } from "../components/AnalysisCharts";
import { EmotionalAlertBanner } from "../components/EmotionalAlertBanner";
import { OperationUpsellBanner } from "../components/OperationUpsellBanner";
import { RubricGapAlertStack } from "../components/RubricGapAlertStack";
import { GeoScoreBreakdown } from "../components/analysis/GeoScoreBreakdown";
import { AiRecognitionSection } from "../components/analysis/AiRecognitionSection";
import { RemediationTaskBoard } from "../components/analysis/RemediationTaskBoard";
import { TierDiagnosisCard } from "../components/TierDiagnosisCard";
import { LoadingCharacter } from "../components/LoadingCharacter";
import { DebateAdviceTeaserBanner } from "../components/DebateAdviceTeaserBanner";
import CircularProgress from "@mui/material/CircularProgress";

const AI_ADVICE_LOADING_MESSAGES = [
  "4人のAIアナリストが議論を始めています...",
  "ANALYSTが市場データを精査中...",
  "SKEPTICが反論ポイントを検討中...",
  "INNOVATORが新しい切り口を提案中...",
  "DIRECTORが最終提言をまとめています...",
] as const;
import { useJobStatusPolling } from "../hooks/useJobStatusPolling";
import {
  competitorLabelsFromProject,
  formatAuditDate,
  parseJobAnalysisDetail,
  resolveAverageSomScore,
  resolveChartShareData,
  resolveChartTrendData,
  type JobAnalysisDetail,
  type JobProjectInfo,
  type ResultDetail,
} from "../types/analysis";
import { isMaintenancePhase } from "../lib/phaseUtils";

const PROCESSING_STATUSES = new Set([
  "CREATED",
  "REALTIME_PROCESSING",
  "FILE_UPLOADED",
  "SUBMITTED",
  "RUNNING",
]);

const PDF_COMPLETED = "COMPLETED";
const PDF_GENERATING = "GENERATING";
const PDF_FAILED = "FAILED";

type PdfRequestResponse = {
  accepted: boolean;
  message: string | null;
};

function isCompletedJobStatus(status: string): boolean {
  return status === "COMPLETED" || status === "SUCCEEDED";
}

function rowShowsDetailedStrategy(
  row: ResultDetail,
  jobMedianModifiedZ: number | null | undefined,
): boolean {
  if (row.significantDeviation === true) {
    return true;
  }
  if (row.significantDeviation === false) {
    return false;
  }
  if (jobMedianModifiedZ == null || Number.isNaN(jobMedianModifiedZ)) {
    return false;
  }
  if (row.modifiedZScore == null || Number.isNaN(row.modifiedZScore)) {
    return false;
  }
  return Math.abs(row.modifiedZScore - jobMedianModifiedZ) >= 1.0;
}

function shouldDataBeReadyForPdf(
  effectiveJobId: string,
  loading: boolean,
  loadError: string | null,
  data: JobAnalysisDetail | null,
): boolean {
  if (!effectiveJobId) {
    return false;
  }
  if (loading || loadError || !data) {
    return false;
  }
  if (isCompletedJobStatus(data.jobStatus)) {
    return Array.isArray(data.results);
  }
  return true;
}

function ProjectInfoBlock({
  project,
  jobIdForReturn,
}: {
  project: JobProjectInfo;
  jobIdForReturn: string;
}): JSX.Element {
  return (
    <div className="pdf-avoid-break mt-4 rounded-lg border border-slate-200 bg-slate-50/80 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-800">プロジェクト</h3>
        <RouterLink
          to={`/projects/${project.projectId}/settings?returnJob=${encodeURIComponent(jobIdForReturn)}`}
          className="pdf-no-print text-xs font-semibold text-indigo-600 hover:text-indigo-800"
        >
          定期監査・通知設定
        </RouterLink>
      </div>
      <p className="mt-2 text-sm text-slate-600">
        <span className="font-medium text-slate-800">名称</span> {project.projectName}
      </p>
      <p className="mt-1 text-sm text-slate-600">
        <span className="font-medium text-slate-800">対象URL</span>{" "}
        <a
          href={project.targetUrl}
          className="break-all text-sky-700 underline hover:text-sky-900"
          target="_blank"
          rel="noreferrer"
        >
          {project.targetUrl}
        </a>
      </p>
      {(() => {
        const profiles = project.competitorProfiles ?? [];
        // 旧ジョブ（プロファイル未保持）は空URLを除外して従来どおりURL表示にフォールバックする。
        const fallbackUrls = project.competitorUrls.filter((u) => u != null && u.trim().length > 0);
        if (profiles.length === 0 && fallbackUrls.length === 0) {
          return null;
        }
        const allSynthetic = profiles.length > 0 && profiles.every((p) => p.synthetic);
        return (
          <div className="mt-2 text-sm text-slate-600">
            <span className="font-medium text-slate-800">競合</span>
            {allSynthetic && (
              <p className="mt-1 text-xs text-slate-500">
                実競合が十分に取得できなかったため、参考基準点を表示しています。
              </p>
            )}
            <ul className="mt-1 space-y-1">
              {profiles.length > 0
                ? profiles.map((p, i) => (
                    <li key={`prof-${i}-${p.name}`} className="break-all">
                      {p.synthetic ? (
                        <span className="inline-flex flex-wrap items-center gap-2">
                          <span>{p.name}</span>
                          <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500">
                            参考基準点・実競合ではない
                          </span>
                        </span>
                      ) : p.websiteUrl != null ? (
                        <a
                          href={p.websiteUrl}
                          className="text-sky-700 underline hover:text-sky-900"
                          target="_blank"
                          rel="noreferrer"
                        >
                          {p.name.trim().length > 0 ? p.name : p.websiteUrl}
                        </a>
                      ) : (
                        <span>{p.name}</span>
                      )}
                    </li>
                  ))
                : fallbackUrls.map((url, i) => (
                    <li key={`url-${i}-${url}`} className="break-all">
                      <a
                        href={url}
                        className="text-sky-700 underline hover:text-sky-900"
                        target="_blank"
                        rel="noreferrer"
                      >
                        {url}
                      </a>
                    </li>
                  ))}
            </ul>
          </div>
        );
      })()}
    </div>
  );
}

function CompletedScoreCell({ value }: { value: string | number }): JSX.Element {
  return (
    <span className="inline-block tabular-nums transition-all duration-500 ease-out">{value}</span>
  );
}

export function JobAnalysisPage(): JSX.Element {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { jobId: jobIdFromRoute } = useParams<{ jobId: string }>();
  const [jobIdInput, setJobIdInput] = useState(jobIdFromRoute ?? "");
  const [data, setData] = useState<JobAnalysisDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // ジョブ完了をポーリングで検知したときに解析データを再取得するためのトリガー。
  const [analysisReloadNonce, setAnalysisReloadNonce] = useState(0);
  const [isReadyForPdf, setIsReadyForPdf] = useState(false);
  const [pdfRequestInFlight, setPdfRequestInFlight] = useState(false);
  const [pdfRequestNotice, setPdfRequestNotice] = useState<string | null>(null);
  const [pdfDownloadInFlight, setPdfDownloadInFlight] = useState(false);

  const effectiveJobId = jobIdFromRoute?.trim() || jobIdInput.trim();

  const {
    jobStatus,
    lastError: jobNotifyError,
    isLoading: jobNotifyLoading,
  } = useJobStatusPolling(effectiveJobId);

  const displayJobId = jobStatus?.jobId ?? data?.jobId ?? null;
  const displayJobStatus = jobStatus?.jobStatus ?? data?.jobStatus ?? null;
  const displayBrand = jobStatus?.brandName ?? data?.brandName ?? null;
  const resolvedStatus = displayJobStatus ?? "";
  const isCompletedDisplay = isCompletedJobStatus(resolvedStatus);
  const displayJobRollupDiagnostic = useMemo(() => {
    const a =
      jobStatus?.diagnosticMessage != null && jobStatus.diagnosticMessage.trim().length > 0
        ? jobStatus.diagnosticMessage
        : null;
    if (a !== null) {
      return a;
    }
    const b =
      data?.jobSummaryDiagnostic != null && data.jobSummaryDiagnostic.trim().length > 0
        ? data.jobSummaryDiagnostic
        : null;
    return b;
  }, [jobStatus?.diagnosticMessage, data?.jobSummaryDiagnostic]);
  const displayJobRollupActions = useMemo(() => {
    if (jobStatus?.recommendedActions != null && jobStatus.recommendedActions.length > 0) {
      return jobStatus.recommendedActions;
    }
    return data?.jobSummaryRecommendedActions ?? [];
  }, [jobStatus?.recommendedActions, data?.jobSummaryRecommendedActions]);
  const displayJobRollupMedZ = useMemo(() => {
    if (jobStatus?.jobMedianModifiedZ != null && !Number.isNaN(jobStatus.jobMedianModifiedZ)) {
      return jobStatus.jobMedianModifiedZ;
    }
    if (data?.jobMedianModifiedZ != null && !Number.isNaN(data.jobMedianModifiedZ)) {
      return data.jobMedianModifiedZ;
    }
    return null;
  }, [jobStatus?.jobMedianModifiedZ, data?.jobMedianModifiedZ]);
  const showJobStrategyBlock = useMemo(() => {
    return (
      (displayJobRollupDiagnostic != null && displayJobRollupDiagnostic.length > 0) ||
      displayJobRollupActions.length > 0 ||
      displayJobRollupMedZ != null
    );
  }, [displayJobRollupDiagnostic, displayJobRollupActions, displayJobRollupMedZ]);
  const isProcessingDisplay =
    resolvedStatus.length > 0 && PROCESSING_STATUSES.has(resolvedStatus);
  const analysisLocked = isProcessingDisplay;
  const resultRows: ResultDetail[] =
    data && isCompletedJobStatus(data.jobStatus) && Array.isArray(data.results) ? data.results : [];
  const isPdfGeneratingUi =
    pdfRequestInFlight || (jobStatus?.pdfStatus != null && jobStatus.pdfStatus === PDF_GENERATING);
  const isPdfMode = searchParams.get("pdf") === "1";
  const brandForCharts = displayBrand ?? "自社";
  const competitorPair = useMemo(
    () => competitorLabelsFromProject(data?.project ?? null),
    [data?.project],
  );
  const chartTrendData = useMemo(
    () => resolveChartTrendData(resultRows, {}, false),
    [resultRows],
  );
  const chartShareData = useMemo(
    () =>
      resolveChartShareData(brandForCharts, competitorPair, resultRows, {}, false),
    [brandForCharts, competitorPair, resultRows],
  );
  const showCharts =
    effectiveJobId.length > 0 && (data !== null || isProcessingDisplay);
  const displaySomForTier = useMemo(() => {
    const avg = resolveAverageSomScore(resultRows, {}, false);
    if (avg !== null) {
      return avg;
    }
    if (isCompletedDisplay && resultRows.length === 0) {
      return 0;
    }
    return null;
  }, [resultRows, isCompletedDisplay]);
  const showTierSkeleton = useMemo(() => {
    if (effectiveJobId.length === 0) {
      return false;
    }
    if (loading) {
      return true;
    }
    if (displaySomForTier !== null) {
      return false;
    }
    return isProcessingDisplay;
  }, [effectiveJobId.length, loading, displaySomForTier, isProcessingDisplay]);
  const showTierBlock = useMemo(() => {
    return effectiveJobId.length > 0 && (showTierSkeleton || displaySomForTier !== null);
  }, [effectiveJobId.length, showTierSkeleton, displaySomForTier]);
  const [workspacePlan, setWorkspacePlan] = useState<WorkspaceSubscriptionPlan | null>(null);
  // PRO・EXPERT はアップセルバナー不要。取得失敗時は false（保守的フォールバック = アップセル表示）
  const isProPlanUi = workspacePlan === "PRO" || workspacePlan === "EXPERT";

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const plan = await fetchWorkspacePlan();
      if (!cancelled) {
        setWorkspacePlan(plan);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const [pdfDelayedReady, setPdfDelayedReady] = useState(false);
  const showPdfReadyFlag = isPdfMode ? pdfDelayedReady : isReadyForPdf;

  const requestPdfReport = useCallback(async () => {
    if (!effectiveJobId.trim()) {
      return;
    }
    setPdfRequestInFlight(true);
    setPdfRequestNotice(null);
    try {
      const res = await apiFetch(`/api/v1/jobs/${effectiveJobId}/pdf/request`, {
        method: "POST",
      });
      let body: PdfRequestResponse | null = null;
      try {
        body = (await responseJsonAsCamel(res)) as PdfRequestResponse;
      } catch {
        body = null;
      }
      if (!res.ok) {
        setPdfRequestInFlight(false);
        if (body?.message != null && body.message.length > 0) {
          setPdfRequestNotice(body.message);
        }
        console.error("pdf request failed", res.status);
        return;
      }
      if (body === null || typeof body.accepted !== "boolean") {
        setPdfRequestInFlight(false);
        setPdfRequestNotice("PDFリクエストの応答が不正です");
        return;
      }
      if (!body.accepted) {
        setPdfRequestInFlight(false);
        setPdfRequestNotice(body?.message ?? "PDFレポートを開始できませんでした");
        return;
      }
    } catch (e: unknown) {
      setPdfRequestInFlight(false);
      console.error("pdf request error", e);
    }
  }, [effectiveJobId]);

  const downloadPdfWithAuth = useCallback(async () => {
    const id = effectiveJobId.trim();
    if (id.length === 0) {
      return;
    }
    setPdfDownloadInFlight(true);
    try {
      await downloadJobPdfWithAuth(id);
    } catch (e: unknown) {
      console.error("pdf download error", e);
    } finally {
      setPdfDownloadInFlight(false);
    }
  }, [effectiveJobId]);

  const openPrintReport = useCallback((): void => {
    const id = effectiveJobId.trim();
    if (id.length === 0) {
      return;
    }
    const token = (import.meta.env.VITE_PDF_INTERNAL_TOKEN ?? "dev-internal-token") as string;
    const url = `/reports/print/${encodeURIComponent(id)}?print=1&internal_token=${encodeURIComponent(token)}`;
    window.open(url, "_blank", "noopener");
  }, [effectiveJobId]);

  const goToJobCreation = useCallback((): void => {
    navigate("/");
  }, [navigate]);

  useEffect(() => {
    if (jobIdFromRoute) {
      setJobIdInput(jobIdFromRoute);
    }
  }, [jobIdFromRoute]);

  useEffect(() => {
    setPdfRequestInFlight(false);
    setPdfDownloadInFlight(false);
    setPdfRequestNotice(null);
  }, [effectiveJobId]);

  useEffect(() => {
    const pdfStatus = jobStatus?.pdfStatus;
    if (pdfStatus === PDF_COMPLETED || pdfStatus === PDF_FAILED) {
      setPdfRequestInFlight(false);
    }
  }, [jobStatus?.pdfStatus]);

  useEffect(() => {
    if (!effectiveJobId) {
      setData(null);
      setLoadError(null);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
    apiFetch(`/api/v1/jobs/${effectiveJobId}/analysis`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          const text = await response.text();
          throw new Error(text || `HTTP ${response.status}`);
        }
        return responseJsonAsCamel(response);
      })
      .then((body: unknown) => {
        const p = parseJobAnalysisDetail(body);
        if (p === null) {
          setLoadError("解析データの形式が不正です");
          setData(null);
        } else {
          setData(p);
        }
      })
      .catch((err: unknown) => {
        if (err instanceof Error && err.name === "AbortError") return;
        const message = err instanceof Error ? err.message : String(err);
        setLoadError(message);
        setData(null);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [effectiveJobId, analysisReloadNonce]);

  // ジョブがポーリングで「完了」に変わったら解析データを1度だけ再取得する。
  // 初回マウント時に処理中の状態で取得するとスコアが0のままになり、手動更新が必要だった問題の根治。
  const jobReportedCompleted = isCompletedJobStatus(jobStatus?.jobStatus ?? "");
  useEffect(() => {
    if (jobReportedCompleted) {
      setAnalysisReloadNonce((n) => n + 1);
    }
  }, [jobReportedCompleted]);

  useEffect(() => {
    const ready = shouldDataBeReadyForPdf(effectiveJobId, loading, loadError, data);
    if (!ready) {
      setIsReadyForPdf(false);
      return;
    }
    let cancelled = false;
    const outer = requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        if (!cancelled) {
          setIsReadyForPdf(true);
        }
      });
    });
    return () => {
      cancelled = true;
      cancelAnimationFrame(outer);
      setIsReadyForPdf(false);
    };
  }, [effectiveJobId, loading, loadError, data]);

  useEffect(() => {
    if (!isReadyForPdf || !isPdfMode) {
      setPdfDelayedReady(false);
      return;
    }
    const t = window.setTimeout(() => setPdfDelayedReady(true), 100);
    return () => window.clearTimeout(t);
  }, [isReadyForPdf, isPdfMode]);

  useEffect(() => {
    if (data === null) {
      return;
    }
    if (!isCompletedJobStatus(data.jobStatus)) {
      return;
    }
    const pid = data.project?.projectId?.trim() ?? "";
    if (pid.length === 0) {
      return;
    }
    const ms = jobStatus !== null ? Date.parse(jobStatus.updatedAt) : Number.NaN;
    mergeBannerJobHint(pid, data.jobId, Number.isNaN(ms) ? Date.now() : ms);
  }, [data, jobStatus]);

  const maintenancePhase =
    data !== null &&
    isCompletedJobStatus(data.jobStatus) &&
    isMaintenancePhase(data.factBasedScore);

  const remediationBoardSection =
    data !== null &&
    Array.isArray(data.remediationTasks) &&
    data.remediationTasks.length > 0 ? (
      <div className="pdf-avoid-break mb-6">
        <RemediationTaskBoard
          jobId={data.jobId}
          tasks={data.remediationTasks}
          onRemediationTaskReplaced={(updated) => {
            setData((prev) => {
              if (prev === null || !Array.isArray(prev.remediationTasks)) {
                return prev;
              }
              const nextTasks = prev.remediationTasks.map((t) =>
                t.id === updated.id ? updated : t,
              );
              return { ...prev, remediationTasks: nextTasks };
            });
          }}
        />
      </div>
    ) : null;

  const geoScoreSection =
    data !== null && isCompletedDisplay && data.scoreBreakdown != null ? (
      <div className="pdf-avoid-break mb-6">
        <GeoScoreBreakdown
          breakdown={data.scoreBreakdown}
          brandName={data.brandName}
          industryMode={data.project?.industryType}
        />
      </div>
    ) : null;

  const aiRecognitionSection =
    data !== null && isCompletedDisplay && data.aiRecognitionSummary != null ? (
      <div className="pdf-avoid-break mb-6">
        <AiRecognitionSection summary={data.aiRecognitionSummary} />
      </div>
    ) : null;

  return (
    <div className="mx-auto max-w-5xl px-6 py-8 text-slate-900">
      <h1 className="pdf-avoid-break mb-6 text-2xl font-semibold tracking-tight text-slate-900">解析結果</h1>
      {data?.emotionalAlert != null ? (
        <div className="pdf-avoid-break mb-4">
          <EmotionalAlertBanner payload={data.emotionalAlert} />
        </div>
      ) : null}
      {jobNotifyLoading && jobStatus === null && effectiveJobId && (
        <div className="pdf-no-print mb-4 flex items-center gap-3 text-slate-600">
          <span
            className="inline-block h-8 w-8 shrink-0 animate-spin rounded-full border-2 border-slate-300 border-t-sky-600"
            aria-hidden
          />
          <span>ジョブ状態を読み込み中です…</span>
        </div>
      )}
      {effectiveJobId && jobNotifyError && (
        <div className="pdf-no-print mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-900">
          <strong className="font-semibold">ジョブ状態の取得に失敗しました</strong>
          <p className="mt-2 text-sm">{jobNotifyError}</p>
        </div>
      )}
      <div className="pdf-no-print mb-6 flex flex-wrap items-center gap-2">
        <label htmlFor="jobId" className="text-sm font-medium text-slate-700">
          ジョブID
        </label>
        <input
          id="jobId"
          type="text"
          value={jobIdInput}
          onChange={(e) => setJobIdInput(e.target.value)}
          placeholder="UUIDを入力"
          className="min-w-[200px] flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-sky-500 focus:outline-none focus:ring-1 focus:ring-sky-500"
        />
      </div>
      {!effectiveJobId && (
        <p className="pdf-no-print text-slate-600">
          ジョブIDを入力するか、URLに /job/&lt;UUID&gt; でアクセスしてください。
        </p>
      )}
      {loading && effectiveJobId && <p className="pdf-no-print text-slate-600">読み込み中です…</p>}
      {loadError && (
        <div className="pdf-no-print mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-900">
          <strong className="font-semibold">取得に失敗しました</strong>
          <pre className="mt-2 whitespace-pre-wrap break-words text-sm">{loadError}</pre>
        </div>
      )}
      {displayJobId && isProcessingDisplay && (
        <div className="pdf-avoid-break pdf-no-print mb-4">
          {/* 解析は数十秒かかる。静止画面で不安・退屈にさせないよう、紫ロボットのアニメ演出を主役にする。 */}
          <LoadingCharacter />
          <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
            <p className="text-sm text-slate-600">
              ステータス: {resolvedStatus}
              {displayBrand ? ` / ブランド: ${displayBrand}` : ""}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              解析結果は完了後に自動で表示されます（ジョブ状態は自動更新されます）。
            </p>
            {data?.project && <ProjectInfoBlock project={data.project} jobIdForReturn={effectiveJobId} />}
          </div>
        </div>
      )}
      {resolvedStatus === "FAILED" && (jobStatus !== null || data !== null) && (
        <div className="pdf-avoid-break mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-900">
          <strong className="font-semibold">ジョブが失敗しました</strong>
          <pre className="mt-2 whitespace-pre-wrap break-words text-sm">
            {jobStatus?.errorMessage ?? data?.errorMessage ?? "理由は記録されていません"}
          </pre>
        </div>
      )}
      {displayJobId && isCompletedDisplay && (
        <div className="pdf-avoid-break mb-6 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-sm text-slate-600">
            <span className="font-medium text-slate-800">ジョブ</span> {displayJobId}
          </p>
          <p className="mt-1 text-sm text-slate-600">
            <span className="font-medium text-slate-800">ステータス</span> {resolvedStatus}
          </p>
          <p className="mt-1 text-sm text-slate-600">
            <span className="font-medium text-slate-800">ブランド</span> {displayBrand ?? "—"}
          </p>
          {jobStatus && (
            <p className="mt-1 text-sm text-slate-600">
              <span className="font-medium text-slate-800">PDF</span>{" "}
              {jobStatus.pdfStatus ?? "未生成"}
            </p>
          )}
          {data?.project && <ProjectInfoBlock project={data.project} jobIdForReturn={effectiveJobId} />}
        </div>
      )}
      {effectiveJobId && jobStatus && (
        <div className="pdf-no-print mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          {pdfRequestNotice !== null && pdfRequestNotice.length > 0 && (
            <div className="w-full rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
              {pdfRequestNotice}
            </div>
          )}
          {jobStatus.pdfStatus === PDF_COMPLETED && (
            <button
              type="button"
              onClick={() => void downloadPdfWithAuth()}
              disabled={pdfDownloadInFlight}
              className={
                pdfDownloadInFlight
                  ? "inline-flex cursor-not-allowed rounded-lg bg-sky-400 px-4 py-2 text-sm font-semibold text-white opacity-70 shadow-sm"
                  : "inline-flex cursor-pointer rounded-lg bg-sky-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-sky-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-500"
              }
            >
              PDFをダウンロード
            </button>
          )}
          {isPdfGeneratingUi && (
            <span
              className="inline-flex items-center gap-2 text-sm text-slate-600"
              aria-live="polite"
              aria-busy="true"
              role="status"
            >
              <CircularProgress size={16} thickness={5} sx={{color:"#0284c7"}} aria-hidden />
              生成中…
            </span>
          )}
          {!isPdfGeneratingUi && (jobStatus.pdfStatus === null || jobStatus.pdfStatus === PDF_FAILED) && (
            <button
              type="button"
              onClick={openPrintReport}
              disabled={analysisLocked || !isCompletedDisplay}
              className={
                !analysisLocked && isCompletedDisplay
                  ? "inline-flex cursor-pointer rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
                  : "inline-flex cursor-not-allowed rounded-lg border border-slate-200 bg-slate-200 px-4 py-2 text-sm font-medium text-slate-600 opacity-60"
              }
              title="新しいタブで印刷プレビューを開きます。ブラウザの『PDFとして保存』をご利用ください。"
            >
              PDFとして保存
            </button>
          )}
          <button
            type="button"
            disabled={analysisLocked}
            onClick={goToJobCreation}
            className={
              !analysisLocked
                ? "inline-flex cursor-pointer rounded-lg border border-slate-300 bg-transparent px-4 py-2 text-sm font-normal text-slate-600 transition-colors hover:border-slate-400 hover:bg-slate-50 hover:text-slate-900"
                : "inline-flex cursor-not-allowed rounded-lg border border-slate-200 bg-transparent px-4 py-2 text-sm font-normal text-slate-400 opacity-70"
            }
          >
            新規ジョブを作成
          </button>
        </div>
      )}
      {data != null &&
        isCompletedJobStatus(data.jobStatus) &&
        data.rubricGaps != null &&
        data.rubricGaps.length > 0 && (
          <RubricGapAlertStack
            alerts={buildEmotionalAlerts(data.rubricGaps, data.project?.industryType ?? "OTHER")}
          />
        )}
      <div id="next-action-section" className="scroll-mt-28 pdf-no-print">
        {!showJobStrategyBlock && isCompletedDisplay && (
          <div className="pdf-avoid-break mb-6 pdf-no-print">
            <LoadingCharacter messages={AI_ADVICE_LOADING_MESSAGES} />
          </div>
        )}
        {showJobStrategyBlock && (
          <div className="pdf-avoid-break mb-6 rounded-xl border border-sky-200 bg-sky-50/80 p-4 shadow-sm">
            <div className="flex items-start justify-between gap-2">
              <h2 className="text-sm font-semibold text-sky-950">ジョブ全体の戦略診断</h2>
              {jobStatus?.adviceSource === "TEMPLATE_FALLBACK" ? (
                <span
                  className="pdf-no-print shrink-0 cursor-help rounded-full border border-amber-200 bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-800"
                  title="AI議論の生成に失敗したため、基本テンプレートで表示しています"
                >
                  簡易分析モード
                </span>
              ) : null}
            </div>
            {displayJobRollupMedZ != null && (
              <p className="mt-1 text-xs text-sky-900/80">
                中央値ベースの改Z&apos;（ジョブ内）: {displayJobRollupMedZ.toFixed(2)}
              </p>
            )}
            {displayJobRollupDiagnostic != null && displayJobRollupDiagnostic.length > 0 ? (
              <p className="mt-2 text-sm leading-relaxed text-sky-950">{displayJobRollupDiagnostic}</p>
            ) : null}
            {displayJobRollupActions.length > 0 ? (
              <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-sky-950">
                {displayJobRollupActions.map((action, idx) => (
                  <li key={`${idx}-${action.slice(0, 40)}`}>{action}</li>
                ))}
              </ul>
            ) : null}
            {!isProPlanUi ? <DebateAdviceTeaserBanner /> : null}
          </div>
        )}
      </div>
      {showTierBlock && (
        <div className="pdf-avoid-break mb-6">
          <TierDiagnosisCard
            somScore={showTierSkeleton ? null : displaySomForTier}
            isProPlan={isProPlanUi}
            skeleton={showTierSkeleton}
          />
        </div>
      )}
      {!maintenancePhase ? remediationBoardSection : null}
      {maintenancePhase ? (
        <div className="pdf-avoid-break mb-6 pdf-no-print">
          <OperationUpsellBanner />
        </div>
      ) : null}
      {geoScoreSection}
      {aiRecognitionSection}
      {showCharts &&
        (isProcessingDisplay ||
          (data !== null && isCompletedJobStatus(data.jobStatus))) && (
          <AnalysisCharts
            isPdfMode={isPdfMode}
            trendData={chartTrendData}
            shareData={chartShareData}
            brandLabel={brandForCharts}
          />
        )}
      {data && isCompletedJobStatus(data.jobStatus) && (
        <div className="pdf-avoid-break overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="pdf-avoid-break border-b border-slate-200 bg-slate-50 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-800">解析結果一覧</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/80">
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">クエリ</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">解析日</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">改Z&apos;</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">GBVS</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">Stage</th>
                  <th className="min-w-[12rem] px-4 py-3 font-semibold text-slate-700">戦略診断・推奨</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">言及状況</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">GEO可視性ランク</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">ネガティブ</th>
                </tr>
              </thead>
              <tbody>
                {resultRows.length === 0 ? (
                  <tr className="pdf-avoid-break">
                    <td colSpan={9} className="px-4 py-8 text-center text-slate-500">
                      完了しましたが、保存された解析結果はまだありません。
                    </td>
                  </tr>
                ) : (
                  resultRows.map((row) => (
                    <tr
                      key={row.resultId}
                      className="pdf-avoid-break border-b border-slate-100 last:border-0 hover:bg-slate-50/60"
                    >
                      <td className="max-w-md px-4 py-3 align-top text-slate-800 transition-all duration-500 ease-out">
                        {row.query}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 align-top text-slate-800">
                        <CompletedScoreCell value={formatAuditDate(row.auditDate)} />
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 align-top text-slate-800">
                        <CompletedScoreCell
                          value={
                            row.modifiedZScore != null && !Number.isNaN(row.modifiedZScore)
                              ? row.modifiedZScore.toFixed(2)
                              : "—"
                          }
                        />
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 align-top text-slate-800">
                        <CompletedScoreCell
                          value={row.gbvsNormalizedScore ?? row.somScore}
                        />
                      </td>
                      <td
                        className="max-w-[14rem] px-4 py-3 align-top text-slate-800"
                        title={
                          row.visibilityStageNarrative != null && row.visibilityStageNarrative !== ""
                            ? row.visibilityStageNarrative
                            : undefined
                        }
                      >
                        {row.visibilityStage != null && row.visibilityStage > 0 ? (
                          <span className="block text-xs leading-snug">
                            <span className="font-semibold text-slate-900">S{row.visibilityStage}</span>
                            {row.visibilityStageBand != null && row.visibilityStageBand !== "" ? (
                              <span className="mt-0.5 block text-slate-600">{row.visibilityStageBand}</span>
                            ) : null}
                          </span>
                        ) : (
                          "—"
                        )}
                      </td>
                      <td className="max-w-sm px-4 py-3 align-top text-xs leading-snug text-slate-700">
                        {rowShowsDetailedStrategy(row, data.jobMedianModifiedZ) ? (
                          <>
                            {row.diagnosticMessage != null && row.diagnosticMessage.length > 0 ? (
                              <p className="mb-2 text-slate-800">{row.diagnosticMessage}</p>
                            ) : null}
                            {row.recommendedActions != null && row.recommendedActions.length > 0 ? (
                              <ul className="list-disc space-y-0.5 pl-4">
                                {row.recommendedActions.map((action, ai) => (
                                  <li key={`${row.resultId}-${ai}`}>{action}</li>
                                ))}
                              </ul>
                            ) : (
                              <span className="text-slate-400">—</span>
                            )}
                          </>
                        ) : (
                          <span className="text-slate-500">偏差基準内</span>
                        )}
                      </td>
                      <td className="px-4 py-3 align-top">
                        {row.brandMentioned ? (
                          <span className="inline-flex rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800 transition-all duration-500 ease-out">
                            言及あり
                          </span>
                        ) : (
                          <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600 transition-all duration-500 ease-out">
                            なし
                          </span>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 align-top text-slate-800">
                        <CompletedScoreCell value={row.mentionRank === null ? "—" : String(row.mentionRank)} />
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 align-top">
                        {row.negativeAlert === true ? (
                          <span className="inline-flex rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-medium text-rose-800">
                            要注意
                          </span>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {maintenancePhase ? remediationBoardSection : null}
      {showPdfReadyFlag && <div id="pdf-ready-flag" aria-hidden="true" />}
    </div>
  );
}
