import { ArrowLeft } from "lucide-react";
import { apiFetch, responseJsonAsCamel } from "../api/apiFetch";
import { useBranding } from "../branding/useBranding";
import { AbsoluteEvaluationSection } from "../components/strategy/AbsoluteEvaluationSection";
import { EmotionalAlertBanner } from "../components/EmotionalAlertBanner";
import { useProjectAssetSnapshots } from "../hooks/useProjectAssetSnapshots";
import { useLatestEmotionalAlert } from "../hooks/useLatestEmotionalAlert";
import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

function localIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth()+1).padStart(2,"0");
  const day = String(d.getDate()).padStart(2,"0");
  return `${y}-${m}-${day}`;
}

function defaultRange(): {from: string; to: string} {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate()-90);
  return {from: localIsoDate(from), to: localIsoDate(to)};
}

function usePrintSnapshot(): boolean {
  const [printing, setPrinting] = useState(false);
  useEffect(() => {
    const mq = window.matchMedia("print");
    const onMq = () => setPrinting(mq.matches);
    const before = () => setPrinting(true);
    const after = () => setPrinting(false);
    mq.addEventListener("change", onMq);
    window.addEventListener("beforeprint", before);
    window.addEventListener("afterprint", after);
    return () => {
      mq.removeEventListener("change", onMq);
      window.removeEventListener("beforeprint", before);
      window.removeEventListener("afterprint", after);
    };
  }, []);
  return printing;
}

// Why: 提案書として印刷したときに、どの案件の推移かが分かるよう、内部のプロジェクトIDではなく名前と対象URLを出す（#180）。
function useProjectLabel(projectId: string): { name: string; targetUrl: string } | null {
  const [label, setLabel] = useState<{ name: string; targetUrl: string } | null>(null);
  useEffect(() => {
    if (!projectId) return;
    let cancelled = false;
    void (async () => {
      try {
        const res = await apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}/settings`);
        if (!res.ok) return;
        const raw = (await responseJsonAsCamel(res)) as Record<string, unknown> | null;
        if (cancelled || raw === null || typeof raw !== "object") return;
        setLabel({
          name: typeof raw.projectName === "string" ? raw.projectName : "",
          targetUrl: typeof raw.targetUrl === "string" ? raw.targetUrl : "",
        });
      } catch {
        // 名前が取れなくても推移のグラフは見られるので、見出しの下を空けたままにする
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId]);
  return label;
}

export default function StrategyDashboardPage(): JSX.Element {
  const {projectId = ""} = useParams<{projectId: string}>();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const returnJobId = searchParams.get("returnJob")?.trim() ?? "";
  const projectLabel = useProjectLabel(projectId);
  const {toolName, logoBlobUrl, brandColor} = useBranding();
  const { emotionalAlert } = useLatestEmotionalAlert(projectId);
  const range = useMemo(() => defaultRange(), []);
  const {data, loading, error} = useProjectAssetSnapshots(projectId, range.from, range.to);
  const isPdfMode = usePrintSnapshot();
  const [pdfReadyFlag, setPdfReadyFlag] = useState(false);

  const printedAt = useMemo(() => new Date().toLocaleString("ja-JP"), []);

  useEffect(() => {
    if (loading || error != null) {
      setPdfReadyFlag(false);
      return;
    }
    let raf1 = 0;
    let raf2 = 0;
    raf1 = window.requestAnimationFrame(() => {
      raf2 = window.requestAnimationFrame(() => {
        setPdfReadyFlag(true);
      });
    });
    return () => {
      if (raf1) window.cancelAnimationFrame(raf1);
      if (raf2) window.cancelAnimationFrame(raf2);
    };
  }, [loading, error, data]);

  useEffect(() => {
    if (!pdfReadyFlag) {
      return;
    }
    const handle = window.requestAnimationFrame(() => {
      const el = document.getElementById("pdf-ready-flag");
      if (el == null) {
        setPdfReadyFlag(false);
      }
    });
    return () => window.cancelAnimationFrame(handle);
  }, [pdfReadyFlag]);

  return (
    <div className="min-h-screen bg-gradient-to-b from-[#f5f2fb] via-[#eae3f4] to-[#f2eef9] pb-16 pt-8 print:bg-none print:bg-white">
      <div
        className="strategy-dashboard-print-root mx-auto px-4 text-slate-900"
        style={{
          width:"min(100%,794px)",
          maxWidth:"100%",
          printColorAdjust:"exact",
          WebkitPrintColorAdjust:"exact",
        }}
      >
        <nav
          className="pdf-no-print mb-6 flex flex-wrap items-center gap-x-1 gap-y-2 border-b border-slate-200/90 pb-4"
          aria-label="ページ導線"
        >
          <button
            type="button"
            onClick={() => navigate(returnJobId.length > 0 ? `/job/${returnJobId}` : "/")}
            className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-sm font-medium text-indigo-600 transition hover:bg-indigo-50 hover:text-indigo-800"
          >
            <ArrowLeft className="h-4 w-4 shrink-0" strokeWidth={2.25} aria-hidden />
            {returnJobId.length > 0 ? "解析結果に戻る" : "ホームに戻る"}
          </button>
        </nav>

        <header className="pdf-avoid-break mb-10 rounded-2xl border border-slate-200 bg-white px-6 py-6 shadow-sm">
          <div className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex items-start gap-4">
              {logoBlobUrl != null && logoBlobUrl.length > 0 ? (
                <img src={logoBlobUrl} alt="" className="h-14 w-14 shrink-0 object-contain" />
              ) : (
                <div className="h-14 w-14 shrink-0 rounded-xl border border-slate-200 bg-slate-50" aria-hidden />
              )}
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{toolName}</p>
                <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">戦略ダッシュボード</h1>
                {projectLabel !== null && projectLabel.name.length > 0 ? (
                  <p className="mt-1 text-sm font-semibold text-slate-700">{projectLabel.name}</p>
                ) : null}
                {projectLabel !== null && projectLabel.targetUrl.length > 0 ? (
                  <p className="mt-0.5 break-all text-xs text-slate-500">{projectLabel.targetUrl}</p>
                ) : null}
                <p className="mt-2 text-sm font-semibold text-slate-800">公式提案書</p>
                <p className="mt-1 text-xs text-slate-600">印刷日時 {printedAt}</p>
              </div>
            </div>
            <div className="pdf-no-print rounded-lg bg-slate-50 px-4 py-3 text-xs text-slate-600">
              <p>
                対象期間 {range.from} 〜 {range.to}
              </p>
            </div>
          </div>
        </header>

        {emotionalAlert != null ? (
          <div className="pdf-no-print pdf-avoid-break mb-6">
            <EmotionalAlertBanner payload={emotionalAlert} />
          </div>
        ) : null}

        {loading && <p className="pdf-no-print mb-6 text-sm text-slate-600">読み込み中…</p>}
        {error != null && (
          <div className="pdf-no-print mb-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
            {error}
          </div>
        )}

        <AbsoluteEvaluationSection data={data} brandColor={brandColor} isPdfMode={isPdfMode} />

        {pdfReadyFlag && <div id="pdf-ready-flag" aria-hidden="true" />}
      </div>
    </div>
  );
}
