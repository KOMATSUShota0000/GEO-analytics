import { Lock, Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";

/**
 * Free（STANDARD）プラン向けの Teaser バナー（仕様書 F-4）。
 *
 * <p>Pro/Expert では解析ごとに新規 4 ペルソナ議論を起動して固有性の高いアドバイスを生成する、
 * という価値をぼかし＋南京錠で提示し、`/pricing` へ誘導する（核③ SaaS グロース）。
 */
export function DebateAdviceTeaserBanner(): JSX.Element {
  const navigate = useNavigate();
  return (
    <div className="pdf-no-print relative mt-4 overflow-hidden rounded-xl border border-violet-200 bg-gradient-to-r from-sky-50 via-violet-50/90 to-indigo-50 shadow-sm">
      {/* ぼかしたダミー議論プレビュー（核心の価値を匂わせる） */}
      <div
        aria-hidden
        className="select-none space-y-1.5 px-4 py-4 text-xs leading-relaxed text-indigo-950/80 blur-sm"
      >
        <p>
          <span className="font-semibold">ANALYST</span>：貴社の業種・競合分布を踏まえると、構造化データの拡充が最優先です。
        </p>
        <p>
          <span className="font-semibold">SKEPTIC</span>：ただしターゲット層の検索意図とズレており、再検証が必要です。
        </p>
        <p>
          <span className="font-semibold">INNOVATOR</span>：独自データセットを起点に、引用されやすい一次情報を……
        </p>
        <p>
          <span className="font-semibold">DIRECTOR</span>：以上を統合すると、貴社固有の打ち手は……
        </p>
      </div>

      {/* 南京錠オーバーレイ + CTA */}
      <button
        type="button"
        onClick={() => navigate("/pricing")}
        className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-white/55 px-4 text-center backdrop-blur-[1px] transition hover:bg-white/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-violet-500"
        aria-label="Proプランの議論駆動アドバイスを確認する"
      >
        <span className="inline-flex items-center gap-1.5 rounded-full bg-violet-600 px-3 py-1 text-xs font-semibold text-white shadow-sm">
          <Lock className="h-3.5 w-3.5" strokeWidth={2.5} aria-hidden />
          Pro限定
        </span>
        <span className="flex items-center gap-1.5 text-sm font-semibold text-indigo-950">
          <Sparkles className="h-4 w-4 text-violet-600" aria-hidden />
          Proプランでは解析ごとに新規AI議論を起動し、より精密なアドバイスを生成します
        </span>
        <span className="text-xs font-medium text-violet-700 underline-offset-2 hover:underline">
          プランを確認する →
        </span>
      </button>
    </div>
  );
}
