import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useJobNotification } from "../hooks/useJobNotification";
import { useJobStreaming } from "../hooks/useJobStreaming";
import {
  formatAuditDate,
  liveMetricsFromParsed,
  type JobAnalysisDetail,
  type JobProjectInfo,
  type ResultDetail,
} from "../types/analysis";

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

function isCompletedJobStatus(status: string): boolean {
  return status === "COMPLETED" || status === "SUCCEEDED";
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

function ProjectInfoBlock({ project }: { project: JobProjectInfo }): JSX.Element {
  return (
    <div className="pdf-avoid-break mt-4 rounded-lg border border-slate-200 bg-slate-50/80 p-4">
      <h3 className="text-sm font-semibold text-slate-800">プロジェクト</h3>
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
      {project.competitorUrls.length > 0 && (
        <div className="mt-2 text-sm text-slate-600">
          <span className="font-medium text-slate-800">競合URL</span>
          <ul className="mt-1 list-inside list-disc space-y-1">
            {project.competitorUrls.map((url) => (
              <li key={url} className="break-all">
                <a href={url} className="text-sky-700 underline hover:text-sky-900" target="_blank" rel="noreferrer">
                  {url}
                </a>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function LiveMetricText({ value }: { value: string }): JSX.Element {
  return (
    <span className="inline-block min-w-[2.25rem] tabular-nums transition-all duration-500 ease-out will-change-transform">
      {value}
    </span>
  );
}

function CompletedScoreCell({ value }: { value: string | number }): JSX.Element {
  return (
    <span className="inline-block tabular-nums transition-all duration-500 ease-out">{value}</span>
  );
}

export function JobAnalysisPage(): JSX.Element {
  const navigate = useNavigate();
  const { jobId: jobIdFromRoute } = useParams<{ jobId: string }>();
  const [jobIdInput, setJobIdInput] = useState(jobIdFromRoute ?? "");
  const [data, setData] = useState<JobAnalysisDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [isReadyForPdf, setIsReadyForPdf] = useState(false);
  const [pdfRequestInFlight, setPdfRequestInFlight] = useState(false);

  const effectiveJobId = jobIdFromRoute?.trim() || jobIdInput.trim();
  const effectiveJobIdRef = useRef(effectiveJobId);
  effectiveJobIdRef.current = effectiveJobId;

  const {
    jobStatus,
    lastError: jobNotifyError,
    isLoading: jobNotifyLoading,
    refetchJobFromRest,
  } = useJobNotification(effectiveJobId);

  const refetchAnalysis = useCallback(async (jobIdStr: string) => {
    const id = jobIdStr.trim();
    if (id.length === 0) {
      return;
    }
    const res = await fetch(`/api/v1/jobs/${id}/analysis`);
    if (!res.ok) {
      return;
    }
    const json = (await res.json()) as JobAnalysisDetail;
    setData(json);
  }, []);

  const handleStreamSettled = useCallback(async () => {
    const id = effectiveJobIdRef.current.trim();
    if (id.length === 0) {
      return;
    }
    await refetchJobFromRest();
    await refetchAnalysis(id);
  }, [refetchJobFromRest, refetchAnalysis]);

  const {
    isStreaming,
    streamError,
    parsedByQueryId,
    connectJobStream,
    disconnectJobStream,
  } = useJobStreaming(handleStreamSettled);

  const displayJobId = jobStatus?.jobId ?? data?.jobId ?? null;
  const displayJobStatus = jobStatus?.jobStatus ?? data?.jobStatus ?? null;
  const displayBrand = jobStatus?.brandName ?? data?.brandName ?? null;
  const resolvedStatus = displayJobStatus ?? "";
  const isCompletedDisplay = isCompletedJobStatus(resolvedStatus);
  const isProcessingDisplay =
    resolvedStatus.length > 0 && PROCESSING_STATUSES.has(resolvedStatus);
  const analysisLocked = isStreaming || isProcessingDisplay;
  const resultRows: ResultDetail[] =
    data && isCompletedJobStatus(data.jobStatus) && Array.isArray(data.results) ? data.results : [];
  const isPdfGeneratingUi =
    pdfRequestInFlight || (jobStatus?.pdfStatus != null && jobStatus.pdfStatus === PDF_GENERATING);

  const requestPdfReport = useCallback(async () => {
    if (!effectiveJobId.trim()) {
      return;
    }
    setPdfRequestInFlight(true);
    try {
      const res = await fetch(`/api/v1/jobs/${effectiveJobId}/pdf/request`, {
        method: "POST",
      });
      if (!res.ok) {
        setPdfRequestInFlight(false);
        const t = await res.text();
        console.error("pdf request failed", res.status, t);
      }
    } catch (e: unknown) {
      setPdfRequestInFlight(false);
      console.error("pdf request error", e);
    }
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
  }, [effectiveJobId]);

  useEffect(() => {
    const pdfStatus = jobStatus?.pdfStatus;
    if (pdfStatus === PDF_COMPLETED || pdfStatus === PDF_FAILED) {
      setPdfRequestInFlight(false);
    }
  }, [jobStatus?.pdfStatus]);

  useEffect(() => {
    const trimmed = effectiveJobId.trim();
    if (trimmed.length === 0) {
      return undefined;
    }
    void connectJobStream(trimmed);
    return () => {
      disconnectJobStream();
    };
  }, [effectiveJobId, connectJobStream, disconnectJobStream]);

  useEffect(() => {
    if (!effectiveJobId) {
      setData(null);
      setLoadError(null);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
    fetch(`/api/v1/jobs/${effectiveJobId}/analysis`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          const text = await response.text();
          throw new Error(text || `HTTP ${response.status}`);
        }
        return response.json() as Promise<JobAnalysisDetail>;
      })
      .then((json) => {
        setData(json);
      })
      .catch((err: unknown) => {
        if (err instanceof Error && err.name === "AbortError") return;
        const message = err instanceof Error ? err.message : String(err);
        setLoadError(message);
        setData(null);
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [effectiveJobId]);

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

  return (
    <div className="mx-auto max-w-5xl px-6 py-8 text-slate-900">
      <h1 className="pdf-avoid-break mb-6 text-2xl font-semibold tracking-tight text-slate-900">解析結果</h1>
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
      {displayJobId && isProcessingDisplay && displayBrand && (
        <div className="pdf-avoid-break pdf-no-print mb-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <p className="text-lg font-medium text-slate-900">解析中です。しばらくお待ちください。</p>
          <p className="mt-2 text-sm text-slate-600">
            ステータス: {resolvedStatus} / ブランド: {displayBrand}
          </p>
          {isStreaming && (
            <p className="mt-2 text-sm text-sky-700">AI応答をストリーミング受信中です…</p>
          )}
          {streamError && (
            <p className="mt-2 text-sm text-amber-800">ストリーム: {streamError}</p>
          )}
          {Object.keys(parsedByQueryId).length > 0 && (
            <div className="mt-4 overflow-hidden rounded-lg border border-slate-200 bg-white">
              <div className="border-b border-slate-200 bg-slate-100 px-3 py-2 text-xs font-semibold text-slate-700">
                ライブプレビュー（partial-json）
              </div>
              <table className="w-full border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-200 bg-slate-50/80">
                    <th className="px-3 py-2 font-semibold text-slate-700">クエリID</th>
                    <th className="px-3 py-2 font-semibold text-slate-700">SoM</th>
                    <th className="px-3 py-2 font-semibold text-slate-700">overall</th>
                    <th className="px-3 py-2 font-semibold text-slate-700">言及</th>
                    <th className="px-3 py-2 font-semibold text-slate-700">順位</th>
                  </tr>
                </thead>
                <tbody>
                  {Object.entries(parsedByQueryId).map(([queryIdKey, parsedValue]) => {
                    const m = liveMetricsFromParsed(parsedValue);
                    return (
                      <tr key={queryIdKey} className="border-b border-slate-100 last:border-0">
                        <td className="px-3 py-2 font-mono text-xs text-slate-800">{queryIdKey}</td>
                        <td className="px-3 py-2 tabular-nums text-slate-800">
                          <LiveMetricText value={m.somScore === null ? "…" : String(m.somScore)} />
                        </td>
                        <td className="px-3 py-2 tabular-nums text-slate-800">
                          <LiveMetricText value={m.overallScore === null ? "…" : String(m.overallScore)} />
                        </td>
                        <td className="px-3 py-2 text-slate-800">
                          <LiveMetricText
                            value={
                              m.brandMentioned === null ? "…" : m.brandMentioned ? "あり" : "なし"
                            }
                          />
                        </td>
                        <td className="px-3 py-2 tabular-nums text-slate-800">
                          <LiveMetricText value={m.mentionRank === null ? "—" : String(m.mentionRank)} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          {data?.project && <ProjectInfoBlock project={data.project} />}
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
          {data?.project && <ProjectInfoBlock project={data.project} />}
        </div>
      )}
      {effectiveJobId && jobStatus && (
        <div className="pdf-no-print mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          {jobStatus.pdfStatus === PDF_COMPLETED && (
            <a
              href={`/api/v1/jobs/${effectiveJobId}/pdf/download`}
              className="inline-flex rounded-lg bg-sky-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-sky-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-500"
              download
            >
              PDFをダウンロード
            </a>
          )}
          {isPdfGeneratingUi && (
            <span className="inline-flex items-center gap-2 text-sm text-slate-600">
              <span
                className="inline-block h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-slate-300 border-t-sky-600"
                aria-hidden
              />
              生成中…
            </span>
          )}
          {!isPdfGeneratingUi && (jobStatus.pdfStatus === null || jobStatus.pdfStatus === PDF_FAILED) && (
            <button
              type="button"
              onClick={requestPdfReport}
              disabled={analysisLocked || !isCompletedDisplay || pdfRequestInFlight}
              className={
                !analysisLocked && isCompletedDisplay && !pdfRequestInFlight
                  ? "inline-flex cursor-pointer rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
                  : "inline-flex cursor-not-allowed rounded-lg border border-slate-200 bg-slate-200 px-4 py-2 text-sm font-medium text-slate-600 opacity-60"
              }
            >
              PDFレポートを生成
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
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">SoMスコア</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">言及状況</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">順位</th>
                </tr>
              </thead>
              <tbody>
                {resultRows.length === 0 ? (
                  <tr className="pdf-avoid-break">
                    <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
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
                        <CompletedScoreCell value={row.somScore} />
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
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
      {isReadyForPdf && <div id="pdf-ready-flag" aria-hidden="true" />}
    </div>
  );
}
