import { Fragment, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { apiFetch, resetCsrfPrime, responseJsonAsCamel } from "../api/apiFetch";
import { getAccessToken, tryRestoreSession } from "../auth/authSession";
import { AnalysisCharts } from "../components/AnalysisCharts";
import { TierDiagnosisCard } from "../components/TierDiagnosisCard";
import { GeoScoreBreakdown } from "../components/analysis/GeoScoreBreakdown";
import { AiRecognitionSection } from "../components/analysis/AiRecognitionSection";
import {
  formatAuditDate,
  mergeJobAnalysisWithPdfContext,
  parseJobAnalysisDetail,
  resolveAverageSomScore,
  resolveChartTrendData,
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

// 解析結果一覧のSoMスコア表示。浮動小数点の生値（例: 11.999999999999998）を避け、
// 画面と同じく gbvsNormalizedScore を優先したうえで小数第1位に整形する。
function formatSomScore(row: ResultDetail): string {
  const v = row.gbvsNormalizedScore ?? row.somScore;
  return Number.isFinite(v) ? v.toFixed(1) : "—";
}

export default function ReportPrintPage(): JSX.Element {
  const { jobId: jobIdFromRoute } = useParams<{ jobId: string }>();
  const [searchParams] = useSearchParams();
  const [data, setData] = useState<JobAnalysisDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
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
  const chartTrendData = useMemo(
    () => resolveChartTrendData(resultRows, {}, false),
    [resultRows],
  );

  // 市場ポジション診断（Tier）はジョブ全体のSoM平均で判定する。画面（JobAnalysisPage）と
  // 同じ算出ロジックを用い、完了済みで結果が空のときだけ 0 にフォールバックする。
  const somForTier = useMemo<number | null>(() => {
    const avg = resolveAverageSomScore(resultRows, {}, false);
    if (avg !== null) {
      return avg;
    }
    if (data != null && isCompletedJobStatus(data.jobStatus) && resultRows.length === 0) {
      return 0;
    }
    return null;
  }, [resultRows, data]);

  useEffect(() => {
    if (typeof window === "undefined") {
      return;
    }
    if (!tokenOk) {
      resetCsrfPrime();
      return;
    }
    resetCsrfPrime();
    return () => {
      resetCsrfPrime();
      try {
        const w = window as unknown as { __PDF_AUTH_TOKEN__?: unknown; __PDF_TENANT_ID__?: unknown };
        delete w.__PDF_AUTH_TOKEN__;
        delete w.__PDF_TENANT_ID__;
      } catch {
        // ignore
      }
    };
  }, [tokenOk]);

  useEffect(() => {
    if (!tokenOk || !effectiveJobId) {
      setData(null);
      setLoadError(null);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setLoadError(null);
    void (async () => {
      try {
        // 新しいタブ（window.open）で開かれた印刷ページは sessionStorage を引き継がず
        // アクセストークンを持たない。HttpOnly リフレッシュ Cookie は同一オリジンの新タブにも
        // 届くため、解析データ取得の前にセッションを復元して 401（未認証）を防ぐ。
        if (getAccessToken() == null) {
          await tryRestoreSession({ force: true });
        }
        const response = await apiFetch(`/api/v1/jobs/${effectiveJobId}/analysis`, {
          signal: controller.signal,
        });
        if (!response.ok) {
          const text = await response.text();
          throw new Error(text || `HTTP ${response.status}`);
        }
        const body = await responseJsonAsCamel(response);
        const p = parseJobAnalysisDetail(body);
        if (p === null) {
          setLoadError("解析データの形式が不正です");
          setData(null);
        } else {
          setData(mergeJobAnalysisWithPdfContext(p));
        }
      } catch (err: unknown) {
        if (err instanceof Error && err.name === "AbortError") return;
        const message = err instanceof Error ? err.message : String(err);
        setLoadError(message);
        setData(null);
      } finally {
        setLoading(false);
      }
    })();
    return () => controller.abort();
  }, [effectiveJobId, tokenOk]);

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
    const timer = setTimeout(() => {
      if (!cancelled) setFontsReady(true);
    }, 3000);
    return () => {
      cancelled = true;
      clearTimeout(timer);
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

  const isErrorState = loadError != null;
  useEffect(() => {
    const ok = (analysisReady && fontsReady && logoReady) || isErrorState;
    setPdfReadyFlag(ok);
  }, [analysisReady, fontsReady, logoReady, isErrorState]);

  const autoPrintRequested = searchParams.get("print") === "1";
  const autoPrintFiredRef = useRef(false);
  useEffect(() => {
    if (!autoPrintRequested || autoPrintFiredRef.current) {
      return;
    }
    if (!pdfReadyFlag || isErrorState) {
      return;
    }
    autoPrintFiredRef.current = true;
    const timer = window.setTimeout(() => {
      try {
        window.print();
      } catch {
        // ignore
      }
    }, 300);
    return () => window.clearTimeout(timer);
  }, [autoPrintRequested, pdfReadyFlag, isErrorState]);

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
    return (
      <div className="p-8 text-red-600">
        <h1 className="text-xl font-bold">PDF Print Error</h1>
        <p>Internal Token Mismatch.</p>
        <p>Expected: {expectedToken}</p>
        <p>Got: {searchParams.get("internal_token")}</p>
        <div id="pdf-ready-flag" aria-hidden="true" />
      </div>
    );
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
      {data && isCompletedJobStatus(data.jobStatus) && (
        <section
          className="pdf-inside-avoid mb-6 bg-white px-8 py-12 text-slate-900"
          style={{
            width: "794px",
            maxWidth: "100%",
            breakInside: "avoid",
            pageBreakInside: "avoid",
          }}
        >
          <h2 className="text-xl font-semibold tracking-tight" style={{ color: "var(--brand-color)" }}>
            ジョブ全体の戦略診断
          </h2>
          {data.jobSummaryDiagnostic != null && data.jobSummaryDiagnostic.trim().length > 0 ? (
            <p className="mt-6 whitespace-pre-wrap text-sm leading-relaxed text-slate-800">
              {data.jobSummaryDiagnostic}
            </p>
          ) : (
            <p className="mt-6 text-sm text-slate-500">ジョブ全体の診断文は準備中です。</p>
          )}
          {(data.jobSummaryRecommendedActions?.length ?? 0) > 0 && (
            <div className="mt-8">
              <h3 className="text-sm font-semibold text-slate-800">推奨アクション</h3>
              <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-slate-700">
                {data.jobSummaryRecommendedActions!.map((a, i) => (
                  <li key={`summary-${i}-${a.slice(0, 24)}`}>{a}</li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}
      {data && isCompletedJobStatus(data.jobStatus) && somForTier !== null && (
        <section
          className="pdf-inside-avoid mb-6"
          style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
        >
          {/* 配布物のためアップセル枠は出さない。isProPlan=true で誘導を抑止する。 */}
          <TierDiagnosisCard somScore={somForTier} isProPlan={true} />
        </section>
      )}
      {data && isCompletedJobStatus(data.jobStatus) && data.scoreBreakdown != null && (
        <section
          className="pdf-inside-avoid mb-6"
          style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
        >
          <GeoScoreBreakdown
            breakdown={data.scoreBreakdown}
            brandName={data.brandName}
            contentEvidence={data.contentEvidence}
            technicalEvidence={data.technicalEvidence}
            industryMode={data.project?.industryType}
          />
        </section>
      )}
      {data && isCompletedJobStatus(data.jobStatus) && data.aiRecognitionSummary != null && (
        <section
          className="pdf-inside-avoid mb-6"
          style={{ breakInside: "avoid", pageBreakInside: "avoid" }}
        >
          <AiRecognitionSection
            summary={data.aiRecognitionSummary}
            queries={resultRows
              .map((r) => r.query)
              .filter((q): q is string => typeof q === "string" && q.trim().length > 0)}
          />
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
                    <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">SoMスコア</th>
                    <th className="whitespace-nowrap px-4 py-3 font-semibold text-slate-700">Stage</th>
                  </tr>
                </thead>
                <tbody>
                  {resultRows.length === 0 ? (
                    <tr>
                      <td colSpan={3} className="px-4 py-8 text-center text-slate-500">
                        完了しましたが、保存された解析結果はまだありません。
                      </td>
                    </tr>
                  ) : (
                    resultRows.map((row) => (
                      <Fragment key={row.resultId}>
                        <tr className="border-b border-slate-100 last:border-0">
                          <td className="max-w-md px-4 py-3 align-top text-slate-800">{row.query}</td>
                          <td className="whitespace-nowrap px-4 py-3 align-top tabular-nums text-slate-800">
                            {formatSomScore(row)}
                          </td>
                          <td className="whitespace-nowrap px-4 py-3 align-top tabular-nums text-slate-800">
                            {row.visibilityStage == null ? "—" : String(row.visibilityStage)}
                          </td>
                        </tr>
                        {row.significantDeviation === true && (
                          <tr className="border-b border-slate-200 bg-slate-50/90">
                            <td colSpan={3} className="px-4 py-4 align-top text-sm text-slate-800">
                              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                                差分診断（例外的乖離）
                              </p>
                              <p className="mt-2 text-xs text-slate-600">
                                解析日 {formatAuditDate(row.auditDate)} / 言及
                                {row.brandMentioned ? "あり" : "なし"} / AI引用順位{" "}
                                {row.mentionRank === null ? "—" : String(row.mentionRank)}
                              </p>
                              {row.diagnosticMessage != null && row.diagnosticMessage.trim().length > 0 ? (
                                <p className="mt-2 whitespace-pre-wrap leading-relaxed">{row.diagnosticMessage}</p>
                              ) : (
                                <p className="mt-2 text-slate-500">詳細診断は準備中です。</p>
                              )}
                              {(row.recommendedActions?.length ?? 0) > 0 && (
                                <ul className="mt-3 list-disc space-y-1 pl-5 text-slate-700">
                                  {row.recommendedActions!.map((a, i) => (
                                    <li key={`${row.resultId}-gap-${i}`}>{a}</li>
                                  ))}
                                </ul>
                              )}
                            </td>
                          </tr>
                        )}
                      </Fragment>
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
        </section>
      )}
      {pdfReadyFlag && <div id="pdf-ready-flag" aria-hidden="true" />}
    </div>
  );
}
