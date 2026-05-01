import type { QueryProposalResponse } from "../types/queryProposal";
import {
  Box,
  Button,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { alpha } from "@mui/material/styles";
import { Check, Copy, User } from "lucide-react";
import { useEffect, useState } from "react";

export type ProposalResultViewProps = {
  data: QueryProposalResponse;
};

type CopyFeedbackState = "idle" | "success" | "error";

const COPY_RESET_MS = 2000;

const ICON_SIZE = 20;

function buildQueriesTsv(data: QueryProposalResponse): string {
  const header = "クエリ\t検索意図";
  const rows = data.queries.map((q) => `${q.queryText}\t${q.intent}`);
  return [header, ...rows].join("\n");
}

async function tryNavigatorClipboardWrite(text: string): Promise<boolean> {
  if (typeof navigator === "undefined" || navigator.clipboard === undefined) {
    return false;
  }
  if (typeof navigator.clipboard.writeText !== "function") {
    return false;
  }
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

function copyViaExecCommand(text: string): boolean {
  if (typeof document === "undefined") {
    return false;
  }
  const ta = document.createElement("textarea");
  ta.value = text;
  ta.setAttribute("readonly", "");
  ta.setAttribute("aria-hidden", "true");
  ta.style.position = "fixed";
  ta.style.left = "-9999px";
  ta.style.top = "0";
  ta.style.opacity = "0";
  document.body.appendChild(ta);
  ta.focus();
  ta.select();
  ta.setSelectionRange(0, text.length);
  try {
    return document.execCommand("copy");
  } catch {
    return false;
  } finally {
    document.body.removeChild(ta);
  }
}

/**
 * Clipboard API → execCommand → prompt の順でコピーを試す。
 * @returns いずれかの手段で利用者がテキストを取得できる見込みが立った場合は true
 */
async function copyTextWithFallbacks(text: string): Promise<boolean> {
  if (await tryNavigatorClipboardWrite(text)) {
    return true;
  }
  if (copyViaExecCommand(text)) {
    return true;
  }
  const message =
    "クリップボードを直接使えない環境です。ダイアログ内のテキストを全選択してコピー（Ctrl+C / ⌘+C）し、OKで閉じてください。";
  const entered = window.prompt(message, text);
  return entered !== null;
}

export function ProposalResultView({ data }: ProposalResultViewProps): JSX.Element {
  const [copyState, setCopyState] = useState<CopyFeedbackState>("idle");

  useEffect(() => {
    if (copyState === "idle") {
      return undefined;
    }
    const id = window.setTimeout(() => {
      setCopyState("idle");
    }, COPY_RESET_MS);
    return () => window.clearTimeout(id);
  }, [copyState]);

  const handleCopyAll = async (): Promise<void> => {
    const text = buildQueriesTsv(data);
    const ok = await copyTextWithFallbacks(text);
    setCopyState(ok ? "success" : "error");
  };

  const copyButtonLabel =
    copyState === "success"
      ? "コピーしました"
      : copyState === "error"
        ? "コピーに失敗しました"
        : "すべてコピー";

  const CopyIcon = copyState === "success" ? Check : Copy;

  return (
    <Paper
      elevation={0}
      variant="outlined"
      sx={{
        p: 2.5,
        borderColor: "divider",
      }}
    >
      <Stack spacing={3}>
        <Stack spacing={2}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <User size={ICON_SIZE} strokeWidth={2} aria-hidden />
            <Typography component="h2" variant="subtitle1" fontWeight={700}>
              ターゲットペルソナ
            </Typography>
          </Stack>
          <Box
            sx={(theme) => ({
              bgcolor: alpha(theme.palette.primary.main, 0.06),
              borderLeft: "4px solid",
              borderColor: "primary.main",
              px: 2,
              py: 1.5,
              borderRadius: 1,
            })}
          >
            <Typography variant="body1" sx={{ whiteSpace: "pre-wrap", lineHeight: 1.7 }}>
              {data.inferredPersona.trim().length > 0 ? data.inferredPersona : "（なし）"}
            </Typography>
          </Box>
        </Stack>

        <Stack spacing={2}>
          <Stack
            direction={{ xs: "column", sm: "row" }}
            alignItems={{ xs: "stretch", sm: "center" }}
            justifyContent="space-between"
            spacing={2}
          >
            <Typography component="h2" variant="subtitle1" fontWeight={700}>
              検索戦略クエリ
            </Typography>
            <Button
              type="button"
              variant="outlined"
              size="small"
              onClick={() => void handleCopyAll()}
              aria-label="すべてのクエリをタブ区切りでクリップボードにコピーする"
              startIcon={<CopyIcon size={18} strokeWidth={2} aria-hidden />}
            >
              {copyButtonLabel}
            </Button>
          </Stack>

          {data.queries.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              クエリがありません。
            </Typography>
          ) : (
            <List disablePadding sx={{ bgcolor: "background.paper" }}>
              {data.queries.map((q, index) => (
                <ListItem
                  key={`${index}-${q.queryText.slice(0, 32)}`}
                  alignItems="flex-start"
                  sx={{ py: 1.5, px: 0 }}
                  divider={index < data.queries.length - 1}
                >
                  <ListItemText
                    primary={
                      <Typography component="span" variant="subtitle1" fontWeight={600}>
                        {q.queryText}
                      </Typography>
                    }
                    secondary={
                      <Typography
                        component="span"
                        variant="body2"
                        color="text.secondary"
                        sx={{ display: "block", mt: 0.5 }}
                      >
                        {q.intent}
                      </Typography>
                    }
                  />
                </ListItem>
              ))}
            </List>
          )}
        </Stack>
      </Stack>
    </Paper>
  );
}
