import { ArrowRight } from "lucide-react";
import type { JobRoadmapItem } from "../types/analysis";

const PHASE_ORDER = ["NOW", "SHORT_TERM", "MID_TERM"] as const;

/**
 * 改善ロードマップ（#77）。
 *
 * <p>改善タスクが「何をやるか」なのに対し、ここは「どの順番で・どのフェーズで」を見せる。
 * フェーズを横に並べて前段の上に次段が積み上がることを示し、タスク一覧との役割の違いを一目で分からせる。
 */
export function RoadmapTimeline({
  items,
  variant = "screen",
}: {
  items: JobRoadmapItem[];
  variant?: "screen" | "print";
}): JSX.Element | null {
  if (items.length === 0) {
    return null;
  }
  const phases = PHASE_ORDER.map((phase) => ({
    phase,
    label: items.find((i) => i.phase === phase)?.phaseLabel ?? phase,
    rows: items.filter((i) => i.phase === phase),
  })).filter((group) => group.rows.length > 0);
  const isPrint = variant === "print";
  return (
    <section
      className={
        isPrint
          ? "pdf-inside-avoid mb-6 rounded-xl border border-slate-200 bg-white p-6"
          : "pdf-avoid-break mb-6 rounded-xl border border-indigo-200 bg-gradient-to-br from-indigo-50/80 to-white p-4 shadow-sm"
      }
      style={isPrint ? { breakInside: "avoid", pageBreakInside: "avoid" } : undefined}
    >
      <h2 className="text-sm font-semibold text-indigo-950">改善ロードマップ</h2>
      <p className="mt-1 text-xs leading-relaxed text-slate-600">
        AI議論が導いた実行順序です。前のフェーズの成果の上に次のフェーズが積み上がります。
      </p>
      <div className={isPrint ? "mt-4 space-y-4" : "mt-4 grid gap-4 md:grid-cols-3"}>
        {phases.map((group, groupIndex) => (
          <div key={group.phase} className="relative">
            <div className="flex items-center gap-2">
              <span className="inline-flex items-center rounded-full bg-indigo-600 px-2.5 py-0.5 text-[11px] font-semibold text-white">
                {group.label}
              </span>
              {!isPrint && groupIndex < phases.length - 1 && (
                <ArrowRight className="hidden h-4 w-4 text-indigo-300 md:block" aria-hidden />
              )}
            </div>
            <ul className="mt-2 space-y-2">
              {group.rows.map((item, index) => (
                <li
                  key={`${group.phase}-${index}-${item.title.slice(0, 24)}`}
                  className="rounded-lg border border-slate-200 bg-white p-3"
                >
                  <p className="text-sm font-medium leading-relaxed text-slate-900">{item.title}</p>
                  {item.rationale.length > 0 && (
                    <p className="mt-1.5 text-xs leading-relaxed text-slate-600">{item.rationale}</p>
                  )}
                  {item.expectedImpact.length > 0 && (
                    <p className="mt-1 text-xs leading-relaxed text-indigo-700">
                      <span className="font-semibold">見込み効果：</span>
                      {item.expectedImpact}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </section>
  );
}
