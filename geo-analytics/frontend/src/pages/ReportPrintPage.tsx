import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { AnalysisCharts } from "../components/AnalysisCharts";
import {
  competitorLabelsFromProject,
  formatAuditDate,
  normalizeAnalyticsSummary,
  parseJobAnalysisDetail,
  resolveChartShareData,
  resolveChartTrendData,
  type AnalyticsSummaryNormalized,
  type JobAnalysisDetail,
  type ResultDetail,
} from "../types/analysis";

const TOKEN_FALLBACK = "dev-internal-token";

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

function pickLogoUrl(d: JobAnalysisDetail): string {
  const a = (d.logoUrl ?? "").trim();
  if (a.length > 0) {
    return a;
  }
  return (d.project?.logoUrl ?? "").trim();
}

function pickBrandColor(d: JobAnalysisDetail): string {
  const a = (d.brandColor ?? "").trim();
  if (a.length > 0) {
    return a;
  }
  const b = (d.project?.brandColor ?? "").trim();
  return b.length > 0 ? b : "#4F46E5";
}

export default function ReportPrintPage(): JSX.Element {
  const { jobId: jobIdFromRoute } = useParams<{ jobId: string }>();
  const [searchParams] = useSearchParams();
  const [data, setData] = useState<JobAnalysisDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [apiCharts, setApiCharts] = useState<AnalyticsSummaryNormalized | undefined>(undefined);
  const [analyticsSettled, setAnalyticsSettled] = useState(false);
  const [fontsReady, setFontsReady] = useState(false);
  const [logoReady, setLogoReady] = useState(false);
  const [pdfReadyFlag, setPdfReadyFlag] = useState(false);

  const effectiveJobId = jobIdFromRoute?.trim() ?? "";
  const expectedToken =
    import.meta.env.VITE_PDF_INTERNAL_TOKEN ?? TOKEN_FALLBACK;
  const tokenOk = searchParams.get("internal_token") === expectedToken;

  const resultRows: ResultDetail[] =
    data && isCompletedJobStatus(data.jobStatus) && Array.isArray(data.results) ? data.results : [];

  const analysisReady = shouldDataBeReadyForPdf(effectiveJobId, loading, loadError, data);

  const brandLabel = data?.brandName ?? "自社";
  const competitorPair = useMemo(
    () => competitorLabelsFromProject(data?.project ?? null),
    [data?.project],
  );
  const chartTrendData = useMemo(() => {
    if (apiCharts !== undefined) {
      return apiCharts.trend;
    }
    return resolveChartTrendData(resultRows, {}, false);
  }, [apiCharts, resultRows]);
  const chartShareData = useMemo(() => {
    if (apiCharts !== undefined) {
      return apiCharts.share;
    }
    return resolveChartShareData(brandLabel, competitorPair, resultRows, {}, false);
  }, [apiCharts, brandLabel, competitorPair, resultRows]);

  const fetchProjectAnalytics = useCallback(async (projectId: string, signal: AbortSignal) => {
    const pid = projectId.trim();
    if (pid.length === 0) {
      return;
    }
    try {
      const res = await fetch(`/api/v1/projects/${pid}/analytics`, { signal });
      if (!res.ok) {
        if (!signal.aborted) {
          setApiCharts(undefined);
        }
        return;
      }
      const body: unknown = await res.json();
      if (!signal.aborted) {
        setApiCharts(normalizeAnalyticsSummary(body) ?? undefined);
      }
    } catch {
      if (!signal.aborted) {
        setApiCharts(undefined);
      }
    }
  }, []);

  useEffect(() => {
    if (!tokenOk || !effectiveJobId) {
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
        return response.json() as Promise<unknown>;
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
  }, [effectiveJobId, tokenOk]);

  useEffect(() => {
    if (!analysisReady || !data?.project?.projectId || !tokenOk) {
      setAnalyticsSettled(true);
      setApiCharts(undefined);
      return;
    }
    setAnalyticsSettled(false);
    const ac = new AbortController();
    void fetchProjectAnalytics(data.project.projectId, ac.signal).finally(() => {
      if (!ac.signal.aborted) {
        setAnalyticsSettled(true);
      }
    });
    return () => ac.abort();
  }, [analysisReady, data?.project?.projectId, tokenOk, fetchProjectAnalytics]);

  useEffect(() => {
    setFontsReady(false);
    if (!analysisReady) {
      return;
    }
    let cancelled = false;
    void document.fonts.ready.then(() => {
      if (!cancelled) {
        setFontsReady(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [analysisReady]);

  useEffect(() => {
    if (!analysisReady || !data) {
      setLogoReady(false);
      return;
    }
    const u = pickLogoUrl(data);
    if (u.length === 0) {
      setLogoReady(true);
    } else {
      setLogoReady(false);
    }
  }, [analysisReady, data]);

  useEffect(() => {
    if (!data) {
      return;
    }
    const el = document.documentElement;
    el.style.setProperty("--brand-color", pickBrandColor(data));
    const logo = pickLogoUrl(data);
    el.style.setProperty(
      "--logo-url",
      logo.length > 0 ? `url("${logo.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}")` : "none",
    );
    return () => {
      el.style.removeProperty("--brand-color");
      el.style.removeProperty("--logo-url");
    };
  }, [data]);

  useEffect(() => {
    const ok = analysisReady && analyticsSettled && fontsReady && logoReady;
    setPdfReadyFlag(ok);
  }, [analysisReady, analyticsSettled, fontsReady, logoReady]);

  const isProcessing = useMemo(() => {
    if (!data) return false;
    return (
      data.jobStatus === "CREATED" ||
      data.jobStatus === "REALTIME_PROCESSING" ||
      data.jobStatus === "FILE_UPLOADED" ||
      data.jobStatus === "SUBMITTED" ||
      data.jobStatus === "RUNNING"
    );
  }, [data]);

  const issuedDateLabel = useMemo(() => {
    return new Date().toLocaleDateString("ja-JP", {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  }, []);

  const logoSrc = data ? pickLogoUrl(data) : "";

  if (!tokenOk) {
    return <div className="p-8 text-slate-500" />;
  }

  return (
    <div
      className="report-print-root mx-auto text-slate-900"
      style={{
        width: "794px",
        maxWidth: "100%",
        printColorAdjust: "exact",
        WebkitPrintColorAdjust: "exact",
      }}
    >
      {loading && effectiveJobId && <p className="text-slate-600">読み込み中です…</p>}
      {loadError && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-900">
          <strong className="font-semibold">取得に失敗しました</strong>
          <pre className="mt-2 whitespace-pre-wrap break-words text-sm">{loadError}</pre>
        </div>
      )}
      {data && (
        <section
          className="flex w-full flex-col items-center justify-center px-8 py-16 text-white"
          style={{
            minHeight: "1123px",
            width: "794px",
            maxWidth: "100%",
            backgroundColor: "var(--brand-color)",
            breakAfter: "page",
            pageBreakAfter: "always",
            printColorAdjust: "exact",
            WebkitPrintColorAdjust: "exact",
          }}
        >
          {logoSrc.length > 0 ? (
            <img
              src={logoSrc}
              alt=""
              className="mb-10 h-28 w-28 object-contain"
              onLoad={() => setLogoReady(true)}
              onError={() => setLogoReady(true)}
            />
          ) : (
            <div
              className="mb-10 h-28 w-28 rounded-2xl border-2 border-white/40 bg-white/10"
              aria-hidden
            />
          )}
          <h1 className="text-center text-3xl font-semibold tracking-tight">GEO 解析レポート</h1>
          <p className="mt-6 text-center text-sm font-light text-white/90">発行日 {issuedDateLabel}</p>
          <p className="mt-10 text-center text-xl font-medium tracking-wide">{data.brandName}</p>
        </section>
      )}
      {data && isProcessing && (
        <section
          className="pdf-inside-avoid mb-6 rounded-xl border border-slate-200 bg-slate-50 p-5"
          style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
        >
          <p className="text-lg font-medium text-slate-900">解析中です。</p>
        </section>
      )}
      {data && data.jobStatus === "FAILED" && (
        <section
          className="pdf-inside-avoid mb-6 rounded-xl border border-red-200 bg-red-50 p-5 text-red-900"
          style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
        >
          <strong className="font-semibold">ジョブが失敗しました</strong>
          <pre className="mt-2 whitespace-pre-wrap break-words text-sm">
            {data.errorMessage ?? "理由は記録されていません"}
          </pre>
        </section>
      )}
      {data && isCompletedJobStatus(data.jobStatus) && (
        <>
          <section
            className="pdf-inside-avoid mb-6"
            style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
          >
            <AnalysisCharts
              isPdfMode={true}
              trendData={chartTrendData}
              shareData={chartShareData}
              brandLabel={brandLabel}
            />
          </section>
          <section
            className="pdf-inside-avoid mb-6 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm"
            style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
          >
            <div className="border-b border-slate-200 bg-slate-50 px-4 py-3">
              <h2 className="text-sm font-semibold" style={{ color: "var(--brand-color)" }}>
                解析結果一覧
              </h2>
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
                    <tr>
                      <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                        完了しましたが、保存された解析結果はまだありません。
                      </td>
                    </tr>
                  ) : (
                    resultRows.map((row) => (
                      <tr key={row.resultId} className="border-b border-slate-100 last:border-0">
                        <td className="max-w-md px-4 py-3 align-top text-slate-800">{row.query}</td>
                        <td className="whitespace-nowrap px-4 py-3 align-top tabular-nums text-slate-800">
                          {formatAuditDate(row.auditDate)}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 align-top tabular-nums text-slate-800">
                          {row.somScore}
                        </td>
                        <td className="px-4 py-3 align-top">
                          {row.brandMentioned ? (
                            <span
                              className="inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium text-white"
                              style={{
                                backgroundColor: "var(--brand-color)",
                                printColorAdjust: "exact",
                                WebkitPrintColorAdjust: "exact",
                              }}
                            >
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
          </section>
        </>
      )}
      {data?.project && (
        <section
          className="pdf-inside-avoid mt-4 text-sm text-slate-600"
          style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
        >
          <p>
            <span className="font-medium" style={{ color: "var(--brand-color)" }}>
              プロジェクト
            </span>{" "}
            {data.project.projectName}
          </p>
          <p className="mt-1 break-all">
            <span className="font-medium text-slate-800">対象URL</span> {data.project.targetUrl}
          </p>
          {data.project.competitorUrls.length > 0 && (
            <div className="mt-1">
              <span className="font-medium text-slate-800">競合URL</span>
              <ul className="ml-4 list-disc">
                {data.project.competitorUrls.map((url) => (
                  <li key={url} className="break-all">
                    {url}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}
      {pdfReadyFlag && <div id="pdf-ready-flag" aria-hidden="true" />}
    </div>
  );
}
