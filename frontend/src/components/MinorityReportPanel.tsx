import { Lightbulb } from "lucide-react";
import type { JobMinorityReport } from "../types/analysis";

/**
 * マイノリティ・レポート（#80）。
 *
 * <p>4ペルソナ議論で DIRECTOR が合意案に採らなかった尖った提案を、採らなかった理由とセットで見せる。
 * 推奨アクションと同じ見た目にすると「やるべきこと」と誤読されるため、意図的に別系統の配色にする。
 */
export function MinorityReportPanel({
  reports,
  variant = "screen",
}: {
  reports: JobMinorityReport[];
  variant?: "screen" | "print";
}): JSX.Element | null {
  if (reports.length === 0) {
    return null;
  }
  const isPrint = variant === "print";
  return (
    <section
      className={
        isPrint
          ? "pdf-inside-avoid mt-8 rounded-xl border border-amber-200 bg-amber-50/60 p-5"
          : "pdf-avoid-break mb-6 rounded-xl border border-amber-200 bg-amber-50/70 p-4 shadow-sm"
      }
      style={isPrint ? { breakInside: "avoid", pageBreakInside: "avoid" } : undefined}
    >
      <div className="flex items-center gap-2">
        <Lightbulb className="h-4 w-4 text-amber-600" aria-hidden />
        <h3 className="text-sm font-semibold text-amber-950">マイノリティ・レポート</h3>
      </div>
      <p className="mt-1 text-xs leading-relaxed text-amber-900/80">
        AI議論で合意には至らなかったものの、条件次第で効く可能性がある提案です。すぐ着手する施策ではありません。
      </p>
      <ul className="mt-3 space-y-3">
        {reports.map((report, index) => (
          <li
            key={`minority-${index}-${report.insight.slice(0, 24)}`}
            className="rounded-lg border border-amber-200 bg-white/80 p-3"
          >
            <p className="text-sm font-medium leading-relaxed text-slate-900">{report.insight}</p>
            {report.conflictReason.length > 0 && (
              <p className="mt-2 text-xs leading-relaxed text-slate-700">
                <span className="font-semibold text-amber-800">採用しなかった理由：</span>
                {report.conflictReason}
              </p>
            )}
            {report.evidence.length > 0 && (
              <p className="mt-1 text-xs leading-relaxed text-slate-600">
                <span className="font-semibold text-amber-800">根拠：</span>
                {report.evidence}
              </p>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
