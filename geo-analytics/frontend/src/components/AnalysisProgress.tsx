import { Bot, Sparkles } from "lucide-react";
import { Box, Fade, Stack, Typography } from "@mui/material";
import { useEffect, useRef, useState } from "react";

const ANALYSIS_PROGRESS_MESSAGES = [
  "対象サイトの内容を読み取っています…",
  "ご入力の戦略コンテキストをモデルに渡しています…",
  "検索意図をドメイン設計に沿って再構成しています…",
  "検索戦略の観点からクエリ案を絞り込んでいます…",
  "品質基準に照らして最終確認しています。あと少しお待ちください。",
] as const;

const INTERVAL_MS = 3500;

const LAST_INDEX = ANALYSIS_PROGRESS_MESSAGES.length - 1;

const FADE_TIMEOUT_MS = 400;

export function AnalysisProgress(): JSX.Element {
  const [messageIndex, setMessageIndex] = useState(0);
  const intervalRef = useRef<number | null>(null);
  const isFinalMessage = messageIndex >= LAST_INDEX;

  useEffect(() => {
    intervalRef.current = window.setInterval(() => {
      setMessageIndex((i) => {
        if (i >= LAST_INDEX) {
          if (intervalRef.current !== null) {
            window.clearInterval(intervalRef.current);
            intervalRef.current = null;
          }
          return i;
        }
        return i + 1;
      });
    }, INTERVAL_MS);

    return () => {
      if (intervalRef.current !== null) {
        window.clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, []);

  return (
    <Stack
      role="status"
      aria-live="polite"
      direction="column"
      alignItems="center"
      spacing={3}
      sx={{ py: 2 }}
    >
      <div className="relative flex h-[4.5rem] w-[4.5rem] shrink-0 items-center justify-center">
        <span
          className={
            isFinalMessage
              ? "absolute inset-0 rounded-full bg-violet-200/35 blur-md opacity-60 transition-opacity duration-500"
              : "absolute inset-0 rounded-full bg-violet-200/40 blur-md animate-pulse"
          }
          aria-hidden
        />
        <Sparkles
          className={
            isFinalMessage
              ? "absolute -right-0.5 -top-1 h-5 w-5 text-violet-500/80 drop-shadow-sm opacity-80 transition-opacity duration-500"
              : "absolute -right-0.5 -top-1 h-5 w-5 text-violet-500 drop-shadow-sm animate-bounce"
          }
          style={isFinalMessage ? undefined : { animationDuration: "1.4s" }}
          aria-hidden
        />
        <Sparkles
          className={
            isFinalMessage
              ? "absolute -left-1 bottom-0 h-4 w-4 text-sky-500/75 opacity-75 transition-opacity duration-500"
              : "absolute -left-1 bottom-0 h-4 w-4 text-sky-500 opacity-90 animate-pulse"
          }
          aria-hidden
        />
        <Bot
          className={
            isFinalMessage
              ? "relative z-10 h-11 w-11 text-indigo-600/85 drop-shadow-md opacity-85 transition-all duration-500"
              : "relative z-10 h-11 w-11 text-indigo-600 drop-shadow-md animate-bounce"
          }
          style={isFinalMessage ? undefined : { animationDuration: "1.1s" }}
          aria-hidden
        />
      </div>

      <Box sx={{ maxWidth: 440, width: "100%", minHeight: "4.5rem" }}>
        <Fade in appear timeout={FADE_TIMEOUT_MS}>
          <Box key={messageIndex}>
            <Typography
              variant="body1"
              color="text.primary"
              align="center"
              sx={{ fontWeight: 500, lineHeight: 1.65 }}
            >
              {ANALYSIS_PROGRESS_MESSAGES[messageIndex]}
            </Typography>
          </Box>
        </Fade>
      </Box>
    </Stack>
  );
}
