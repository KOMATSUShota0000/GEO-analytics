import { DebateAdviceTeaserBanner } from "../DebateAdviceTeaserBanner";

export interface JobDiagnosisPanelProps {
  diagnostic: string;
  templateFallback: boolean;
  showTeaser: boolean;
  /** 上位プランへの誘導の飛び先。公開デモだけが公開のプラン比較（`/plans`）を渡す（#188） */
  upgradeTo?: string;
}

/**
 * 総合診断の欄。
 *
 * Why: 結果画面と公開デモの両方で、同じ見た目・同じ文言で出すため、結果画面から切り出した（#188）。
 */
export function JobDiagnosisPanel({
  diagnostic,
  templateFallback,
  showTeaser,
  upgradeTo,
}: JobDiagnosisPanelProps): JSX.Element {
  return (
    <div className="pdf-avoid-break mb-6 rounded-xl border border-sky-200 bg-sky-50/80 p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <h2 className="text-sm font-semibold text-sky-950">総合診断</h2>
        {templateFallback ? (
          <span
            className="pdf-no-print shrink-0 cursor-help rounded-full border border-amber-200 bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-800"
            title="AI議論の生成に失敗したため、基本テンプレートで表示しています"
          >
            簡易分析モード
          </span>
        ) : null}
      </div>
      <p className="mt-1 text-xs text-sky-800">いまAIの回答で自社がどう扱われているかと、その理由です。</p>
      <p className="mt-2 text-sm leading-relaxed text-sky-950">{diagnostic}</p>
      {showTeaser ? <DebateAdviceTeaserBanner upgradeTo={upgradeTo} /> : null}
    </div>
  );
}
