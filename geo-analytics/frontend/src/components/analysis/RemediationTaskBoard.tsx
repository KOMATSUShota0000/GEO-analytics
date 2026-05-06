import { Box, Chip, Stack, Typography } from "@mui/material";
import { AlertTriangle, Bolt, HardHat } from "lucide-react";
import { Marked } from "marked";
import { useMemo } from "react";
import { SafeHtmlRenderer } from "../SafeHtmlRenderer";
import type { RemediationTask, RemediationTaskCategory, RemediationTaskPriority } from "../../types/analysis";

const markedRenderer = new Marked({
  gfm: true,
  breaks: true,
});

function renderMarkdown(source: string): string {
  if (typeof source !== "string" || source.length === 0) {
    return "";
  }
  const parsed = markedRenderer.parse(source);
  if (typeof parsed === "string") {
    return parsed;
  }
  return "";
}

function priorityColor(priority: RemediationTaskPriority): string {
  if (priority === "S") {
    return "#dc2626";
  }
  if (priority === "A") {
    return "#f59e0b";
  }
  return "#0ea5e9";
}

function priorityLabel(priority: RemediationTaskPriority): string {
  if (priority === "S") {
    return "S 級・最優先";
  }
  if (priority === "A") {
    return "A 級";
  }
  return "B 級";
}

function categoryHeader(category: RemediationTaskCategory): { title: string; subtitle: string; icon: JSX.Element; color: string } {
  if (category === "SPIKE") {
    return {
      title: "Spike（即効策）",
      subtitle: "即時に反映可能な施策",
      icon: <Bolt size={16} aria-hidden />,
      color: "#0ea5e9",
    };
  }
  return {
    title: "Slab（根本策）",
    subtitle: "抜本的な改修が必要な施策",
    icon: <HardHat size={16} aria-hidden />,
    color: "#9333ea",
  };
}

interface TaskCardProps {
  task: RemediationTask;
}

function TaskCard({ task }: TaskCardProps): JSX.Element {
  const isDanger = task.priority === "S";
  const html = useMemo(() => renderMarkdown(task.content), [task.content]);
  const borderColor = isDanger ? "rgba(220,38,38,0.65)" : "rgba(226,232,240,0.9)";
  const shadow = isDanger ? "0 12px 36px rgba(220,38,38,0.18)" : "0 6px 24px rgba(15,23,42,0.05)";
  const accent = priorityColor(task.priority);
  const impactPct = Math.max(0, Math.min(1, task.impactScore));
  return (
    <Box
      sx={{
        position: "relative",
        borderRadius: "14px",
        border: `1.5px solid ${borderColor}`,
        backgroundColor: isDanger ? "rgba(254,242,242,0.65)" : "#ffffff",
        boxShadow: shadow,
        padding: "16px 18px",
        overflow: "hidden",
      }}
    >
      <Box
        sx={{
          position: "absolute",
          insetInlineStart: 0,
          insetBlockStart: 0,
          insetBlockEnd: 0,
          width: 4,
          backgroundColor: accent,
        }}
      />
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={1.5}>
        <Stack spacing={0.5} sx={{ minWidth: 0, flex: 1 }}>
          <Typography
            variant="subtitle1"
            sx={{ fontWeight: 700, color: "#0f172a", lineHeight: 1.4, wordBreak: "break-word" }}
          >
            {task.title}
          </Typography>
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            <Chip
              size="small"
              label={priorityLabel(task.priority)}
              icon={isDanger ? <AlertTriangle size={14} aria-hidden /> : undefined}
              sx={{
                backgroundColor: `${accent}1a`,
                color: accent,
                fontWeight: 700,
                "& .MuiChip-icon": {
                  color: accent,
                  marginLeft: "6px",
                },
              }}
            />
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
        </Stack>
      </Stack>
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
    </Box>
  );
}

interface ColumnProps {
  category: RemediationTaskCategory;
  tasks: RemediationTask[];
}

function Column({ category, tasks }: ColumnProps): JSX.Element {
  const header = categoryHeader(category);
  return (
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1.25 }}>
        <Box
          sx={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            width: 30,
            height: 30,
            borderRadius: 8,
            backgroundColor: `${header.color}1a`,
            color: header.color,
          }}
        >
          {header.icon}
        </Box>
        <Stack>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, color: "#0f172a" }}>
            {header.title}
          </Typography>
          <Typography variant="caption" sx={{ color: "#64748b" }}>
            {header.subtitle}
          </Typography>
        </Stack>
      </Stack>
      <Stack spacing={1.5}>
        {tasks.length === 0 ? (
          <Box
            sx={{
              borderRadius: "12px",
              border: "1px dashed rgba(148,163,184,0.5)",
              padding: "16px",
              color: "#64748b",
              textAlign: "center",
              fontSize: 13,
            }}
          >
            該当する{category === "SPIKE" ? "即効策" : "根本策"}は提示されていません。
          </Box>
        ) : (
          tasks.map((task) => <TaskCard key={task.id} task={task} />)
        )}
      </Stack>
    </Box>
  );
}

export interface RemediationTaskBoardProps {
  tasks: RemediationTask[];
}

export function RemediationTaskBoard({ tasks }: RemediationTaskBoardProps): JSX.Element | null {
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return null;
  }
  const sorted = [...tasks].sort((a, b) => {
    if (a.priority !== b.priority) {
      return a.priority.localeCompare(b.priority);
    }
    return b.impactScore - a.impactScore;
  });
  const spikeTasks = sorted.filter((t) => t.category === "SPIKE");
  const slabTasks = sorted.filter((t) => t.category === "SLAB");
  return (
    <Box
      sx={{
        borderRadius: "16px",
        border: "1px solid rgba(226,232,240,0.9)",
        background: "linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)",
        boxShadow: "0 8px 30px rgba(15,23,42,0.06)",
        padding: "20px 24px",
      }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Stack>
          <Typography variant="h6" sx={{ fontWeight: 700, color: "#0f172a" }}>
            改善タスク（Spike & Slab）
          </Typography>
          <Typography variant="caption" sx={{ color: "#64748b" }}>
            S 級は赤色枠の DANGER シグナルとして優先処理してください
          </Typography>
        </Stack>
      </Stack>
      <Stack direction={{ xs: "column", md: "row" }} spacing={3}>
        <Column category="SPIKE" tasks={spikeTasks} />
        <Column category="SLAB" tasks={slabTasks} />
      </Stack>
    </Box>
  );
}
