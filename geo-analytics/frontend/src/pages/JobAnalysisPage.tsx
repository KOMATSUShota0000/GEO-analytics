import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useJobNotification } from "../hooks/useJobNotification";
import type { JobAnalysisDetail } from "../types/analysis";

export interface ResultSummary {
  query: string;
  somScore: number;
  brandMentioned: boolean;
  mentionRank: number | null;
}

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
  resultsLoading: boolean,
  resultsError: string | null,
  resultSummaries: ResultSummary[] | null,
): boolean {
  if (!effectiveJobId) {
    return false;
  }
  if (loading || loadError || !data) {
    return false;
  }
  if (isCompletedJobStatus(data.jobStatus)) {
    return !resultsLoading && !resultsError && resultSummaries !== null;
  }
  return true;
}

export function JobAnalysisPage(): JSX.Element {
  const { jobId: jobIdFromRoute } = useParams<{ jobId: string }>();
  const [jobIdInput, setJobIdInput] = useState(jobIdFromRoute ?? "");
  const [data, setData] = useState<JobAnalysisDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [resultSummaries, setResultSummaries] = useState<ResultSummary[] | null>(null);
  const [resultsLoading, setResultsLoading] = useState(false);
  const [resultsError, setResultsError] = useState<string | null>(null);
  const [isReadyForPdf, setIsReadyForPdf] = useState(false);

  const effectiveJobId = jobIdFromRoute?.trim() || jobIdInput.trim();

  const {
    jobStatus,
    lastError: jobNotifyError,
    isLoading: jobNotifyLoading,
  } = useJobNotification(effectiveJobId);

  const displayJobId = jobStatus?.jobId ?? data?.jobId ?? null;
  const displayJobStatus = jobStatus?.jobStatus ?? data?.jobStatus ?? null;
  const displayBrand = jobStatus?.brandName ?? data?.brandName ?? null;
  const resolvedStatus = displayJobStatus ?? "";
  const isCompletedDisplay = isCompletedJobStatus(resolvedStatus);
  const isProcessingDisplay =
    resolvedStatus.length > 0 && PROCESSING_STATUSES.has(resolvedStatus);

  const requestPdfReport = useCallback(async () => {
    if (!effectiveJobId.trim()) {
      return;
    }
    try {
      const res = await fetch(`/api/v1/jobs/${effectiveJobId}/pdf/request`, {
        method: "POST",
      });
      if (!res.ok) {
        const t = await res.text();
        console.error("pdf request failed", res.status, t);
      }
    } catch (e: unknown) {
      console.error("pdf request error", e);
    }
  }, [effectiveJobId]);

  useEffect(() => {
    if (jobIdFromRoute) {
      setJobIdInput(jobIdFromRoute);
    }
  }, [jobIdFromRoute]);

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
    if (!effectiveJobId || !data || !isCompletedJobStatus(data.jobStatus)) {
      setResultSummaries(null);
      setResultsError(null);
      setResultsLoading(false);
      return;
    }
    const controller = new AbortController();
    setResultsLoading(true);
    setResultsError(null);
    fetch(`/api/v1/jobs/${effectiveJobId}/results`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          const text = await response.text();
          throw new Error(text || `HTTP ${response.status}`);
        }
        return response.json() as Promise<ResultSummary[]>;
      })
      .then((rows) => {
        setResultSummaries(rows);
      })
      .catch((err: unknown) => {
        if (err instanceof Error && err.name === "AbortError") return;
        const message = err instanceof Error ? err.message : String(err);
        setResultsError(message);
        setResultSummaries(null);
      })
      .finally(() => setResultsLoading(false));
    return () => controller.abort();
  }, [effectiveJobId, data?.jobStatus]);

  useEffect(() => {
    const ready = shouldDataBeReadyForPdf(
      effectiveJobId,
      loading,
      loadError,
      data,
      resultsLoading,
      resultsError,
      resultSummaries,
    );
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
  }, [
    effectiveJobId,
    loading,
    loadError,
    data,
    resultsLoading,
    resultsError,
    resultSummaries,
  ]);

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
        </div>
      )}
      {effectiveJobId && isReadyForPdf && jobStatus && (
        <div className="pdf-no-print mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          {jobStatus.pdfStatus === PDF_COMPLETED && (
            <a
              href={`/api/v1/jobs/${effectiveJobId}/pdf/download`}
              className="inline-flex rounded-lg bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700"
              download
            >
              PDFをダウンロード
            </a>
          )}
          {jobStatus.pdfStatus === PDF_GENERATING && (
            <span className="text-sm text-slate-600">PDFを生成しています…</span>
          )}
          {(jobStatus.pdfStatus === null || jobStatus.pdfStatus === PDF_FAILED) && (
            <button
              type="button"
              onClick={requestPdfReport}
              disabled={!isCompletedDisplay}
              className={
                isCompletedDisplay
                  ? "inline-flex cursor-pointer rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
                  : "inline-flex cursor-not-allowed rounded-lg border border-slate-200 bg-slate-200 px-4 py-2 text-sm font-medium text-slate-600 opacity-60"
              }
            >
              PDFレポートを生成
            </button>
          )}
        </div>
      )}
      {data && isCompletedJobStatus(data.jobStatus) && resultsLoading && (
        <p className="pdf-no-print mb-4 text-slate-600">解析結果を読み込み中です…</p>
      )}
      {data && isCompletedJobStatus(data.jobStatus) && resultsError && (
        <div className="pdf-no-print mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-900">
          <strong className="font-semibold">解析結果の取得に失敗しました</strong>
          <pre className="mt-2 whitespace-pre-wrap break-words text-sm">{resultsError}</pre>
        </div>
      )}
      {data && isCompletedJobStatus(data.jobStatus) && resultSummaries !== null && !resultsLoading && (
        <div className="pdf-avoid-break overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
          <div className="pdf-avoid-break border-b border-slate-200 bg-slate-50 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-800">解析結果一覧</h2>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/80">
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">クエリ</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">SoMスコア</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">言及状況</th>
                  <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">順位</th>
                </tr>
              </thead>
              <tbody>
                {resultSummaries.length === 0 ? (
                  <tr className="pdf-avoid-break">
                    <td colSpan={4} className="px-4 py-8 text-center text-slate-500">
                      完了しましたが、保存された解析結果はまだありません。
                    </td>
                  </tr>
                ) : (
                  resultSummaries.map((row, index) => (
                    <tr
                      key={`${row.query}-${index}`}
                      className="pdf-avoid-break border-b border-slate-100 last:border-0 hover:bg-slate-50/60"
                    >
                      <td className="max-w-md px-4 py-3 align-top text-slate-800">{row.query}</td>
                      <td className="whitespace-nowrap px-4 py-3 align-top tabular-nums text-slate-800">
                        {row.somScore}
                      </td>
                      <td className="px-4 py-3 align-top">
                        {row.brandMentioned ? (
                          <span className="inline-flex rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800">
                            言及あり
                          </span>
                        ) : (
                          <span className="inline-flex rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600">
                            なし
                          </span>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 align-top tabular-nums text-slate-800">
                        {row.mentionRank === null ? "—" : String(row.mentionRank)}
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
