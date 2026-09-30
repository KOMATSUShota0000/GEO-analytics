import { useMemo, useState } from "react";
import type { DebateUtterance, RemediationTask } from "../../types/analysis";
import { DEBATE_CARD_ORDER, DEBATE_PERSONAS, buildDebateItems } from "./debateDisplay";
import { DebatePersonaIcon, DebateTranscript } from "./DebateTranscript";

export type DebateSummaryRowProps = {
  utterances: DebateUtterance[];
  /** 改善タスク（画面の番号順）。根拠に番号で出てくるタスクのタイトルを添えるのに使う。 */
  tasks: RemediationTask[];
};

/**
 * 議論が終わったあとの1行（#200）。総合診断の上に畳んで置き、「議論の流れを見る」で発言の並びを開く。
 *
 * <p>Why: 結果を読みに来た人には、まず総合診断と改善ロードマップを見せる。議論の中身は、結論の拠り所を
 * 確かめたい人が開けばよい。件数は実際の発言から数える（進行役の一言は AI の発言ではないので数えない）。
 */
export function DebateSummaryRow({ utterances, tasks }: DebateSummaryRowProps): JSX.Element | null {
  const [open, setOpen] = useState(false);
  const items = useMemo(() => buildDebateItems(utterances, tasks), [utterances, tasks]);
  const spoken = items.filter((item) => item.speaker !== null);
  if (spoken.length === 0) {
    return null;
  }
  const rounds = utterances.reduce((max, u) => Math.max(max, u.round ?? 0), 0);
  const evidenceCount = spoken.filter((item) => item.evidence !== null).length;
  const facts = [
    rounds > 0 ? `${rounds}ラウンド` : null,
    `発言${spoken.length}件`,
    evidenceCount > 0 ? `根拠にした測定結果と改善タスク ${evidenceCount}か所` : null,
  ].filter((part): part is string => part !== null);

  return (
    <section className="pdf-no-print mb-6 rounded-2xl border border-slate-200 bg-white px-4 py-[18px] sm:px-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between sm:gap-5">
        <div className="flex items-center gap-4">
          <div className="flex shrink-0">
            {DEBATE_CARD_ORDER.map((speaker, index) => (
              <DebatePersonaIcon
                key={speaker}
                persona={DEBATE_PERSONAS[speaker]}
                className={`h-[34px] w-[34px] border-2 border-white text-sm ${index > 0 ? "-ml-2" : ""}`}
              />
            ))}
          </div>
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="text-base font-bold text-slate-900">4人のAIの議論から、改善の進め方が決まりました</span>
            <span className="text-[13px] text-slate-600">{facts.join("・")}</span>
          </div>
        </div>
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="inline-flex h-11 shrink-0 cursor-pointer items-center justify-center rounded-lg bg-indigo-50 px-4 text-sm font-bold text-indigo-800 transition-colors hover:bg-indigo-100"
        >
          {open ? "閉じる" : "議論の流れを見る"}
        </button>
      </div>
      {open && (
        <div className="mt-5 border-t border-slate-200 pt-5">
          <DebateTranscript items={items} />
        </div>
      )}
    </section>
  );
}

const ABSENCE_TEXT: Record<"SKIPPED" | "FAILED", string> = {
  SKIPPED: "この解析では、4人のAIの議論は行っていません。総合診断は測定結果から作成しています。",
  FAILED:
    "4人のAIの議論が途中で止まったため、議論なしで総合診断を作成しました。議論の分のチケットは消費していません。",
};

/**
 * 議論が無かった解析の一言（#200。文言は 2026-09-30 オーナー確定）。
 *
 * <p>Why: 議論は全プランで走る前提なので、無かったときに何も言わないと、議論を経た結論と区別がつかない。
 * 失敗したときは途中までの発言を消しているので（ADR-095）、見せられるのはこの一言だけ。
 * チケットは、失敗した時点か、遅くとも1時間ごとの掃除（StaleReservationSweeper）で全額返る。
 */
export function DebateAbsenceNote({ status }: { status: "SKIPPED" | "FAILED" }): JSX.Element {
  return (
    <p className="pdf-no-print mb-6 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-[13px] leading-relaxed text-slate-600">
      {ABSENCE_TEXT[status]}
    </p>
  );
}
