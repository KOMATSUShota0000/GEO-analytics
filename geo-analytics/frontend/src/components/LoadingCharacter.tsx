import { Bot, Sparkles } from "lucide-react";
import { useEffect, useState } from "react";

const MESSAGES = [
  "AIがウェブサイトを調査しています...",
  "ブランドの露出度を計測中...",
  "ユーザーのアテンションをシミュレーション中...",
  "SoMスコアを数理モデルで算出しています...",
] as const;

const INTERVAL_MS = 3200;

export type LoadingCharacterProps = {
  className?: string;
};

export function LoadingCharacter({ className = "" }: LoadingCharacterProps): JSX.Element {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    const id = window.setInterval(() => {
      setIndex((i) => (i + 1) % MESSAGES.length);
    }, INTERVAL_MS);
    return () => window.clearInterval(id);
  }, []);
  return (
    <div
      role="status"
      aria-live="polite"
      className={`rounded-2xl border border-indigo-200/70 bg-gradient-to-br from-sky-50 via-violet-50/90 to-indigo-50 p-5 shadow-md shadow-indigo-100/40 ${className}`.trim()}
    >
      <div className="flex flex-col items-center gap-4 sm:flex-row sm:items-center sm:gap-6">
        <div className="relative flex h-[4.5rem] w-[4.5rem] shrink-0 items-center justify-center">
          <span className="absolute inset-0 rounded-full bg-violet-200/40 blur-md animate-pulse" aria-hidden />
          <Sparkles
            className="absolute -right-0.5 -top-1 h-5 w-5 text-violet-500 drop-shadow-sm animate-bounce"
            style={{ animationDuration: "1.4s" }}
            aria-hidden
          />
          <Sparkles
            className="absolute -left-1 bottom-0 h-4 w-4 text-sky-500 opacity-90 animate-pulse"
            aria-hidden
          />
          <Bot
            className="relative z-10 h-11 w-11 text-indigo-600 drop-shadow-md animate-bounce"
            style={{ animationDuration: "1.1s" }}
            aria-hidden
          />
        </div>
        <p className="min-h-[2.75rem] flex-1 text-center text-sm font-medium leading-relaxed text-indigo-950/90 transition-opacity duration-300 sm:text-left">
          {MESSAGES[index]}
        </p>
      </div>
      <div className="mt-4 h-1.5 w-full overflow-hidden rounded-full bg-indigo-100/80">
        <div className="h-full w-1/4 rounded-full bg-gradient-to-r from-sky-400 via-violet-500 to-indigo-500 animate-shimmer" />
      </div>
    </div>
  );
}
