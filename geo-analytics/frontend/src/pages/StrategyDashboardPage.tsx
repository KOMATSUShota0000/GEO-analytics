import { useBranding } from "../branding/useBranding";
import { AbsoluteEvaluationSection } from "../components/strategy/AbsoluteEvaluationSection";
import { RelativeEvaluationSection } from "../components/strategy/RelativeEvaluationSection";
import { LockedInsightCallout } from "../components/policy/LockedInsightCallout";
import { useProjectAssetSnapshots } from "../hooks/useProjectAssetSnapshots";
import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";

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

export default function StrategyDashboardPage(): JSX.Element {
  const {projectId = ""} = useParams<{projectId: string}>();
  const {toolName, logoBlobUrl, brandColor} = useBranding();
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
    <div className="min-h-screen bg-slate-50/80 pb-16 pt-8">
      <div
        className="strategy-dashboard-print-root mx-auto px-4 text-slate-900"
        style={{
          width:"min(100%,794px)",
          maxWidth:"100%",
          printColorAdjust:"exact",
          WebkitPrintColorAdjust:"exact",
        }}
      >
        <nav className="pdf-no-print mb-6 text-sm text-slate-600">
          <Link to="/" className="text-sky-700 underline-offset-2 hover:underline">
            ホーム
          </Link>
          <span className="mx-2">/</span>
          <span className="text-slate-800">戦略ダッシュボード</span>
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
                <p className="mt-2 text-sm font-semibold text-slate-800">公式提案書</p>
                <p className="mt-1 text-xs text-slate-600">印刷日時 {printedAt}</p>
              </div>
            </div>
            <div className="pdf-no-print rounded-lg bg-slate-50 px-4 py-3 text-xs text-slate-600">
              <p>
                対象期間 {range.from} 〜 {range.to}
              </p>
              <p className="mt-1 break-all">プロジェクト ID {projectId}</p>
            </div>
          </div>
        </header>

        <div className="pdf-no-print mb-6 rounded-xl border border-sky-100 bg-sky-50/80 px-4 py-3">
          <LockedInsightCallout message="🔒 基礎スコアが上位プランで開示されます（プレースホルダ）。" />
        </div>

        {loading && <p className="pdf-no-print mb-6 text-sm text-slate-600">読み込み中…</p>}
        {error != null && (
          <div className="pdf-no-print mb-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">
            {error}
          </div>
        )}

        <AbsoluteEvaluationSection data={data} brandColor={brandColor} isPdfMode={isPdfMode} />
        <RelativeEvaluationSection />

        {pdfReadyFlag && <div id="pdf-ready-flag" aria-hidden="true" />}
      </div>
    </div>
  );
}
