import { Lock } from "lucide-react";

export interface LockedInsightCalloutProps {
  message: string;
}

export function LockedInsightCallout({ message }: LockedInsightCalloutProps): JSX.Element {
  const trimmed = message.trimStart();
  if (trimmed.startsWith("🔒")) {
    return (
      <div
        role="note"
        className="flex gap-3 rounded-xl border border-amber-200 bg-amber-50/95 px-4 py-3 text-slate-900 shadow-sm"
      >
        <Lock className="mt-0.5 h-5 w-5 shrink-0 text-amber-800" strokeWidth={2} aria-hidden />
        <p className="text-sm leading-relaxed">{trimmed}</p>
      </div>
    );
  }
  return <p className="text-sm leading-relaxed text-slate-700">{message}</p>;
}
