import { useEffect, useMemo, useState } from "react";
import type { DebateUtterance, RemediationTask } from "../../types/analysis";
import {
  DEBATE_CARD_ORDER,
  DEBATE_PERSONAS,
  DEBATE_SPEAKING_SEQUENCE,
  DEBATE_STEP_LABELS,
  buildDebateItems,
} from "./debateDisplay";
import { DebatePersonaIcon, DebateTranscript } from "./DebateTranscript";

const TYPE_TICK_MS = 35;
const CHARS_PER_TICK = 2;
const PAUSE_BETWEEN_UTTERANCES_MS = 1000;

function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export type DebateLivePanelProps = {
  /** 話した順。問い合わせのたびに、そこまでの全件が届く。 */
  utterances: DebateUtterance[];
  /** 議論が終わり、総合診断と改善ロードマップを出せる。 */
  finished: boolean;
  /** 改善タスク（画面の番号順）。根拠に番号で出てくるタスクのタイトルを添えるのに使う。 */
  tasks: RemediationTask[];
  onShowResults: () => void;
};

/**
 * 解析結果の画面の待ち時間に、4人のAIの議論を1発言ずつ見せる（#199）。
 *
 * <p>Why: 発言は数秒〜十数秒おきに届く。届いた発言は1つずつ順に、文字を流して見せる。まとめて届いても
 * 飛ばさない。議論が終わっても、最後の発言を流し終えるまでは結果に切り替えない。
 * 動きを減らす設定（prefers-reduced-motion）の人には、文字を流さず一度に出す。
 */
export function DebateLivePanel({ utterances, finished, tasks, onShowResults }: DebateLivePanelProps): JSX.Element {
  const [reducedMotion] = useState(prefersReducedMotion);
  const items = useMemo(() => buildDebateItems(utterances, tasks), [utterances, tasks]);
  // Why: 開いた時点ですでに届いていた発言は流し直さない。いちばん新しい発言から流す。
  const [pos, setPos] = useState(() => ({ index: Math.max(0, items.length - 1), chars: 0 }));

  useEffect(() => {
    if (reducedMotion) {
      return undefined;
    }
    const item = items[pos.index];
    if (item === undefined) {
      return undefined;
    }
    const full = pos.chars >= item.text.length;
    if (full && pos.index >= items.length - 1) {
      return undefined;
    }
    const id = window.setTimeout(
      () =>
        setPos((p) =>
          full
            ? { index: p.index + 1, chars: 0 }
            : { index: p.index, chars: Math.min(item.text.length, p.chars + CHARS_PER_TICK) },
        ),
      full ? PAUSE_BETWEEN_UTTERANCES_MS : TYPE_TICK_MS,
    );
    return () => window.clearTimeout(id);
  }, [items, pos, reducedMotion]);

  const lastIndex = items.length - 1;
  const typingIndex = reducedMotion ? lastIndex : Math.min(pos.index, lastIndex);
  const current = items[typingIndex];
  const caughtUp = reducedMotion || (pos.index >= lastIndex && pos.chars >= current.text.length);
  const done = finished && caughtUp;
  // Why: 届いた発言を流し終えたら、次に話す人を「発言中」にする。順番は固定なので、届いた件数から決まる。
  const nextSpeaker = DEBATE_SPEAKING_SEQUENCE[utterances.length] ?? null;
  const activeSpeaker = done ? null : caughtUp ? nextSpeaker : current.speaker;
  const activeStep = caughtUp ? (utterances.length < 3 ? 0 : utterances.length < 6 ? 1 : 2) : current.step;

  return (
    <section className="pdf-no-print mb-6 flex flex-col gap-5 rounded-2xl border border-indigo-200 bg-white p-4 sm:p-7">
      <div className="flex flex-col gap-1.5">
        <h2 className="text-lg font-bold text-slate-900 sm:text-xl">4人のAIが、改善の進め方を話し合っています</h2>
        <p className="text-sm leading-relaxed text-slate-600">
          測定結果と改善タスクをもとに、何から、いつまでに取り組むかを決めています。終わると、ここに総合診断と改善ロードマップが表示されます。
        </p>
      </div>

      <ol className="grid grid-cols-3 gap-2">
        {DEBATE_STEP_LABELS.map((label, step) => {
          const complete = done || step < activeStep;
          const active = !done && step === activeStep;
          return (
            <li key={label} className="flex flex-col gap-1.5">
              <span
                className={`h-1.5 rounded-full ${complete ? "bg-indigo-600" : active ? "bg-indigo-300" : "bg-slate-200"}`}
              />
              <span className={`text-xs font-bold sm:text-[13px] ${complete || active ? "text-indigo-800" : "text-slate-500"}`}>
                {label}
                {complete ? "　済み" : active ? "　進行中" : ""}
              </span>
            </li>
          );
        })}
      </ol>

      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {DEBATE_CARD_ORDER.map((speaker) => {
          const persona = DEBATE_PERSONAS[speaker];
          const speaking = activeSpeaker === speaker;
          return (
            <div
              key={speaker}
              className={`flex items-center gap-2.5 rounded-xl border-2 p-3 ${speaking ? "bg-white" : "bg-slate-50"}`}
              style={{ borderColor: speaking ? persona.color : "#e2e8f0" }}
            >
              <DebatePersonaIcon persona={persona} className="h-9 w-9 text-[15px]" />
              <div className="flex min-w-0 flex-col gap-0.5">
                <span className="text-sm font-bold text-slate-900">{persona.name}</span>
                <span className="text-xs leading-normal text-slate-600">{persona.role}</span>
                {speaking && (
                  <span className="animate-pulse text-xs font-bold" style={{ color: persona.color }}>
                    発言中…
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <DebateTranscript items={items.slice(0, typingIndex + 1)} typing={reducedMotion ? null : pos} />

      {done && (
        <div className="flex flex-col gap-4 rounded-xl border border-emerald-200 bg-emerald-50 px-5 py-[18px] sm:flex-row sm:items-center sm:justify-between">
          <div className="flex flex-col gap-1">
            <span className="text-base font-bold text-emerald-800">議論がまとまりました</span>
            <span className="text-[13px] text-emerald-800">総合診断と改善ロードマップを表示します。</span>
          </div>
          <button
            type="button"
            onClick={onShowResults}
            className="inline-flex h-11 shrink-0 cursor-pointer items-center justify-center rounded-lg bg-emerald-700 px-5 text-[15px] font-bold text-white transition-colors hover:bg-emerald-800"
          >
            結果を見る
          </button>
        </div>
      )}
    </section>
  );
}
