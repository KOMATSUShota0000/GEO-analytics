import { Box, Stack, Typography } from "@mui/material";
import { useEffect, useMemo, useState } from "react";
import type { DebateStreamPersona, OnboardingNarrationLogEntry } from "../../types/onboardingDebateStream";

const FACADE_REPLACEMENTS: Array<{ pattern: RegExp; text: string }> = [
  { pattern: /検索エンジン/g, text: "AI推奨インターフェース" },
  { pattern: /SEO/gi, text: "AI検索での見え方（GEO）" },
  { pattern: /検索順位/g, text: "AI検索推奨率" },
  { pattern: /順位付け/g, text: "推奨のされやすさ" },
  { pattern: /ランキング/g, text: "推奨スコア" },
];

function toGeoFacadeCopy(raw: string): string {
  let s = raw;
  for (const { pattern, text } of FACADE_REPLACEMENTS) {
    s = s.replace(pattern, text);
  }
  return s;
}

function personaLabel(persona: DebateStreamPersona | null): string {
  switch (persona) {
    case "ANALYST":
      return "アナリスト（構造的シグナル担当）";
    case "INNOVATOR":
      return "イノベーター（情報の独自性）";
    case "SKEPTIC":
      return "スケプティック（健全性監査）";
    case "DIRECTOR":
      return "ディレクター（収束オーケストレーション）";
    case "SYSTEM":
      return "オーケストレーター";
    default:
      return "参加エージェント";
  }
}

function personaTypingMs(persona: DebateStreamPersona | null): number {
  switch (persona) {
    case "ANALYST":
      return 26;
    case "INNOVATOR":
      return 17;
    case "SKEPTIC":
      return 21;
    case "DIRECTOR":
      return 24;
    case "SYSTEM":
      return 29;
    default:
      return 22;
  }
}

function TypingLine(props: { text: string; intervalMs: number }): JSX.Element {
  const { text, intervalMs } = props;
  const [revealed, setRevealed] = useState(0);

  useEffect(() => {
    if (text.length === 0) {
      return;
    }
    setRevealed(0);
    let i = 0;
    const id = window.setInterval(() => {
      i += 1;
      if (i >= text.length) {
        window.clearInterval(id);
        setRevealed(text.length);
        return;
      }
      setRevealed(i);
    }, intervalMs);
    return () => window.clearInterval(id);
  }, [text, intervalMs]);

  const shown = text.slice(0, revealed);
  return (
    <Typography component="span" variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
      {shown}
      {revealed < text.length && (
        <Box
          component="span"
          sx={{
            display: "inline-block",
            width: "0.35em",
            height: "1em",
            ml: 0.15,
            verticalAlign: "text-bottom",
            bgcolor: "primary.main",
            opacity: 0.55,
            animation: "obCaret 0.9s step-end infinite",
            "@keyframes obCaret": {
              "0%, 49%": { opacity: 0.55 },
              "50%, 100%": { opacity: 0.05 },
            },
          }}
          aria-hidden
        />
      )}
    </Typography>
  );
}

export interface OnboardingNarrationMonitorProps {
  entries: OnboardingNarrationLogEntry[];
}

export function OnboardingNarrationMonitor(props: OnboardingNarrationMonitorProps): JSX.Element {
  const { entries } = props;
  const stablePrevious = useMemo(() => (entries.length >= 2 ? entries.slice(0, -1) : []), [entries]);
  const last = entries.length >= 1 ? entries[entries.length - 1] : null;

  return (
    <Stack spacing={1.75} sx={{ maxHeight: 360, overflowY: "auto", pr: 0.5 }}>
      <Typography variant="subtitle2" color="text.secondary" fontWeight={700}>
        ディベート実況（AI検索推奨率に効く構造と独自性）
      </Typography>
      {stablePrevious.map((row) => (
        <Box
          key={row.id}
          sx={{
            borderLeft: 3,
            borderColor:
              row.persona === "SKEPTIC"
                ? "warning.main"
                : row.persona === "INNOVATOR"
                  ? "secondary.main"
                  : row.persona === "DIRECTOR"
                    ? "success.main"
                    : "primary.light",
            pl: 1.25,
          }}
        >
          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.35 }}>
            {personaLabel(row.persona)}
          </Typography>
          <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>
            {toGeoFacadeCopy(row.message)}
          </Typography>
        </Box>
      ))}
      {last !== null && (
        <Box
          sx={{
            borderLeft: 3,
            borderColor:
              last.persona === "SKEPTIC"
                ? "warning.main"
                : last.persona === "INNOVATOR"
                  ? "secondary.main"
                  : last.persona === "DIRECTOR"
                    ? "success.main"
                    : "primary.main",
            pl: 1.25,
            bgcolor: "action.hover",
            borderRadius: 1,
            py: 0.75,
          }}
        >
          <Typography variant="caption" color="primary" display="block" fontWeight={700} sx={{ mb: 0.35 }}>
            {personaLabel(last.persona)}
          </Typography>
          <TypingLine text={toGeoFacadeCopy(last.message)} intervalMs={personaTypingMs(last.persona)} />
        </Box>
      )}
      {entries.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          接続が確立されると、各ペルソナの実況がリアルタイムで流れます。
        </Typography>
      )}
    </Stack>
  );
}
