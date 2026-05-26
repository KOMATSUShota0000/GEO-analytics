import LockOutlined from "@mui/icons-material/LockOutlined";
import { AlertTriangle } from "lucide-react";
import { alpha, useTheme } from "@mui/material/styles";
import { Marked } from "marked";
import { useEffect, useMemo, useRef, useState } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Skeleton from "@mui/material/Skeleton";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { buildClipboardText } from "../../lib/taskUtils";
import { SafeHtmlRenderer } from "../SafeHtmlRenderer";
import { TaskToneToggle } from "./TaskToneToggle";
import type {
  RemediationTask,
  RemediationTaskCategory,
  RemediationTaskPriority,
} from "../../types/analysis";

const markedRenderer = new Marked({
  gfm: true,
  breaks: true,
});

function renderMarkdown(source: string): string {
  if (typeof source !== "string" || source.length === 0) {
    return "";
  }
  const parsed = markedRenderer.parse(source);
  return typeof parsed === "string" ? parsed : "";
}

function priorityAccent(priority: RemediationTaskPriority): {
  chipLabel: string;
  chipSx: Record<string, unknown>;
  stripe: string;
} {
  if (priority === "S") {
    return {
      chipLabel: "S 級・最優先",
      chipSx: {
        backgroundImage: "linear-gradient(135deg,#fef08a 0%,#fbbf24 35%,#f87171 100%)",
        color: "#7f1d1d",
        fontWeight: 800,
        border: "1px solid rgba(234,179,8,0.85)",
      },
      stripe: "#b45309",
    };
  }
  if (priority === "A") {
    return {
      chipLabel: "A 級",
      chipSx: {
        backgroundColor: "rgba(37,99,235,0.14)",
        color: "#1d4ed8",
        fontWeight: 700,
        border: "1px solid rgba(59,130,246,0.45)",
      },
      stripe: "#2563eb",
    };
  }
  return {
    chipLabel: "B 級",
    chipSx: {
      backgroundColor: "rgba(100,116,139,0.12)",
      color: "#475569",
      fontWeight: 700,
      border: "1px solid rgba(148,163,184,0.5)",
    },
    stripe: "#64748b",
  };
}

function categoryChipLabel(cat: RemediationTaskCategory): string {
  return cat === "SPIKE" ? "Spike（即効）" : "Slab（根本）";
}

export type TaskCardProps = {
  task: RemediationTask;
  jobId: string;
  onTaskReplaced?: (next: RemediationTask) => void;
};

export function TaskCard({ task, jobId, onTaskReplaced }: TaskCardProps): JSX.Element {
  const theme = useTheme();
  const accent = priorityAccent(task.priority);
  const masked = task.isMasked === true;
  const isS = !masked && task.priority === "S";
  const html = useMemo(
    () => (masked ? "" : renderMarkdown(task.content)),
    [task.content, masked],
  );
  const [copiedFeedback, setCopiedFeedback] = useState(false);
  const [regenBusy, setRegenBusy] = useState(false);
  const timerRef = useRef<number | null>(null);

  useEffect(() => {
    return (): void => {
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current);
      }
    };
  }, []);

  const handleCopy = (): void => {
    if (masked) {
      return;
    }
    if (timerRef.current !== null) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    void navigator.clipboard.writeText(buildClipboardText(task)).then(() => {
      setCopiedFeedback(true);
      timerRef.current = window.setTimeout(() => {
        setCopiedFeedback(false);
        timerRef.current = null;
      }, 2200);
    });
  };

  const impactPct = Math.max(0, Math.min(1, task.impactScore));

  return (
    <Card
      elevation={masked ? 0 : isS ? 4 : 1}
      sx={{
        position: "relative",
        borderRadius: "14px",
        border: masked
          ? "1px solid rgba(226,232,240,0.95)"
          : isS
            ? "1.5px solid rgba(217,119,6,0.55)"
            : "1px solid rgba(226,232,240,0.95)",
        overflow: "hidden",
        backgroundColor: masked ? "rgba(241,245,249,0.95)" : isS ? "rgba(255,251,235,0.75)" : "#ffffff",
        opacity: masked ? 0.92 : 1,
      }}
    >
      <Box
        sx={{
          position: "absolute",
          insetInlineStart: 0,
          insetBlockStart: 0,
          insetBlockEnd: 0,
          width: "4px",
          backgroundColor: masked ? "#94a3b8" : accent.stripe,
          zIndex: 1,
        }}
      />
      <CardContent sx={{ pb: 2 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
          <Stack spacing={0.75} sx={{ minWidth: 0, flex: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: "#0f172a", wordBreak: "break-word" }}>
              {task.title}
            </Typography>
            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
              <Chip
                size="small"
                label={accent.chipLabel}
                icon={isS ? <AlertTriangle size={14} aria-hidden /> : undefined}
                sx={{
                  ...accent.chipSx,
                  ...(isS
                    ? {
                        "& .MuiChip-icon": { color: "#991b1b", marginInlineStart: "6px" },
                      }
                    : {}),
                }}
              />
              <Chip size="small" variant="outlined" label={categoryChipLabel(task.category)} />
              <Chip
                size="small"
                label={`Impact ${Math.round(impactPct * 100)}%`}
                sx={{
                  backgroundColor: "rgba(15,23,42,0.04)",
                  color: "#475569",
                  fontVariantNumeric: "tabular-nums",
                  fontWeight: 600,
                }}
              />
            </Stack>
            {masked || onTaskReplaced === undefined ? null : (
              <TaskToneToggle
                jobId={jobId}
                task={task}
                onTaskReplaced={onTaskReplaced}
                onBusyChange={setRegenBusy}
              />
            )}
          </Stack>
          <Button size="small" variant="outlined" disabled={masked || regenBusy} onClick={handleCopy}>
            {copiedFeedback ? "コピー完了" : "内容をコピー"}
          </Button>
        </Stack>
        {masked ? (
          <Stack alignItems="center" sx={{ mt: 2, px: 0.5 }}>
            <LockOutlined sx={{ fontSize: 54, color: "text.secondary", mb: 1.5 }} aria-hidden />
            <Paper
              elevation={0}
              sx={{
                width: "100%",
                px: 2,
                py: 1.75,
                borderRadius: "10px",
                border: `1px solid ${alpha(theme.palette.info.main, 0.35)}`,
                backgroundColor: alpha(theme.palette.info.light, theme.palette.mode === "dark" ? 0.12 : 0.45),
              }}
            >
              <Typography
                sx={{
                  fontWeight: 700,
                  fontSize: 14,
                  lineHeight: 1.65,
                  color: "#0f172a",
                  textAlign: "center",
                  wordBreak: "break-word",
                }}
              >
                {task.content}
              </Typography>
            </Paper>
          </Stack>
        ) : regenBusy ? (
          <Stack spacing={1} sx={{ mt: 1.5 }}>
            <Skeleton variant="rounded" height={22} sx={{ bgcolor: "rgba(15,23,42,0.08)" }} />
            <Skeleton variant="rounded" height={22} sx={{ bgcolor: "rgba(15,23,42,0.08)" }} />
            <Skeleton variant="rounded" height={140} sx={{ bgcolor: "rgba(15,23,42,0.06)" }} />
          </Stack>
        ) : (
          <Box
            sx={{
              mt: 1.5,
              color: "#1f2937",
              fontSize: 14,
              lineHeight: 1.7,
              "& p": { margin: "0 0 8px" },
              "& ul, & ol": { paddingInlineStart: "1.4em", margin: "0 0 8px" },
              "& code": {
                backgroundColor: "rgba(15,23,42,0.06)",
                padding: "1px 6px",
                borderRadius: 4,
                fontSize: 12.5,
              },
              "& strong": { color: "#0f172a" },
              "& h1, & h2, & h3": {
                fontSize: 14,
                fontWeight: 700,
                margin: "10px 0 6px",
              },
            }}
          >
            <SafeHtmlRenderer html={html} />
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
