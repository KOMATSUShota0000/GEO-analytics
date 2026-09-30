import { CornerUpLeft, LineChart } from "lucide-react";
import { DEBATE_STEP_LABELS, type DebateItem, type DebatePersonaView } from "./debateDisplay";

export type DebateTranscriptProps = {
  items: DebateItem[];
  /** 文字を流している途中の発言と、そこまでに出した文字数。流していないときは null。 */
  typing?: { index: number; chars: number } | null;
};

/**
 * 議論の発言の並び（#199）。議論中の表示と、終わったあとの「議論の流れを見る」で使う。
 *
 * <p>Why: 読み上げソフトには、流している途中の文ではなく発言の全文を渡す。根拠の枠は、その発言を流し終えてから出す。
 */
export function DebateTranscript({ items, typing = null }: DebateTranscriptProps): JSX.Element {
  return (
    <div className="flex flex-col gap-[18px]">
      {items.map((item, index) => {
        const isTyping = typing !== null && index === typing.index && typing.chars < item.text.length;
        return (
          <div key={item.key} className="flex flex-col gap-3.5">
            {(index === 0 || items[index - 1].step !== item.step) && (
              <div className="flex items-center gap-3">
                <span className="h-px grow bg-slate-200" />
                <span className="text-xs font-bold text-slate-500">{DEBATE_STEP_LABELS[item.step]}</span>
                <span className="h-px grow bg-slate-200" />
              </div>
            )}
            <div className="flex items-start gap-3.5">
              <DebatePersonaIcon persona={item.persona} className="h-10 w-10 text-base" />
              <div
                className="flex min-w-0 grow flex-col gap-2 rounded-xl px-4 py-3.5"
                style={{ background: item.persona.bubble }}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-bold text-slate-900">{item.persona.name}</span>
                  <span className="text-xs text-slate-600">{item.persona.role}</span>
                  {item.reply !== null && (
                    <span
                      className="inline-flex items-center gap-1 rounded-full border bg-white px-2 py-px text-xs font-bold"
                      style={{ color: item.persona.color, borderColor: item.persona.color }}
                    >
                      <CornerUpLeft className="h-3 w-3" strokeWidth={2.4} aria-hidden />
                      {item.reply}
                    </span>
                  )}
                </div>
                <p className="text-[15px] leading-[1.8] text-slate-900">
                  <span className="sr-only">{item.text}</span>
                  <span aria-hidden>
                    {isTyping ? item.text.slice(0, typing.chars) : item.text}
                    {isTyping && (
                      <span className="ml-0.5 inline-block h-[1em] w-[0.4em] animate-pulse bg-indigo-600 align-text-bottom" />
                    )}
                  </span>
                </p>
                {item.evidence !== null && !isTyping && (
                  <div className="flex gap-2.5 rounded-lg border border-slate-200 bg-white px-3 py-2.5">
                    <LineChart className="mt-[3px] h-4 w-4 shrink-0 text-slate-500" aria-hidden />
                    <div className="flex min-w-0 flex-col gap-0.5">
                      <span className="text-xs font-bold text-slate-600">{item.evidence.heading}</span>
                      {item.evidence.body.length > 0 && (
                        <span className="text-sm leading-relaxed text-slate-700">{item.evidence.body}</span>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function DebatePersonaIcon({
  persona,
  className,
}: {
  persona: DebatePersonaView;
  className: string;
}): JSX.Element {
  return (
    <span
      className={`flex shrink-0 items-center justify-center rounded-full font-bold text-white ${className}`}
      style={{ background: persona.color }}
      aria-hidden
    >
      {persona.letter}
    </span>
  );
}
