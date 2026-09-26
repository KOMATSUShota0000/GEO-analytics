import { Check } from "lucide-react";

export const PRODUCT_NAME = "GEO Analytics";

const POINTS = [
  "4人のAIが議論して、改善策をまとめる",
  "競合と比べた「AIでの存在感」を図で示す",
  "取り組む順番と時間割まで、そのまま提案資料に",
] as const;

/** 製品の目印。ロゴの画像ができるまでの代わり（#164）。 */
export function BrandMark({ onGradient = false }: { onGradient?: boolean }) {
  return (
    <span
      aria-hidden
      className={[
        "flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-lg font-bold",
        // Why: グラデーションの上にグラデーションの目印を置くと溶け込むので、左半分では白地に藍の文字にする。
        onGradient
          ? "bg-white text-indigo-600 shadow-lg shadow-indigo-900/20"
          : "bg-gradient-to-br from-indigo-600 to-violet-600 text-white shadow-sm",
      ].join(" ")}
    >
      G
    </span>
  );
}

/** ログイン画面の左半分。パソコンの幅でだけ出す（スマホでは入力欄だけにする）。 */
export default function LoginBrandPanel() {
  return (
    <aside className="relative hidden overflow-hidden bg-gradient-to-br from-indigo-600 via-violet-600 to-sky-600 text-white lg:flex lg:flex-col lg:justify-between lg:p-12 xl:p-16">
      <div aria-hidden className="pointer-events-none absolute -left-24 -top-24 h-80 w-80 rounded-full bg-white/10 blur-3xl" />
      <div aria-hidden className="pointer-events-none absolute -bottom-32 right-0 h-96 w-96 rounded-full bg-sky-300/20 blur-3xl" />

      <div className="relative flex items-center gap-3">
        <BrandMark onGradient />
        <span className="text-lg font-semibold tracking-wide">{PRODUCT_NAME}</span>
      </div>

      <div className="relative max-w-lg">
        <h2 className="text-4xl font-bold leading-tight">
          AI検索で、
          <br />
          選ばれるサイトへ。
        </h2>
        <p className="mt-5 text-base leading-relaxed text-white/85">
          ChatGPT や Google の AI が答えるとき、お客さまのサイトが紹介されているか。
          診断から改善の順番まで、提案に使える形でまとめます。
        </p>
        <ul className="mt-10 space-y-4">
          {POINTS.map((point) => (
            <li key={point} className="flex items-start gap-3">
              <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-white/20 ring-1 ring-white/30">
                <Check className="h-4 w-4" aria-hidden />
              </span>
              <span className="text-base leading-relaxed">{point}</span>
            </li>
          ))}
        </ul>
      </div>

      <p className="relative text-sm text-white/70">Web制作会社・代理店のための AI検索診断</p>
    </aside>
  );
}
