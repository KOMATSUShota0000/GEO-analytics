import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { hasLockedRemediationTasks } from "../../lib/taskUtils";
import type { RemediationTask } from "../../types/analysis";
import { TaskCardGroup } from "./TaskCardGroup";

export interface RemediationTaskBoardProps {
  tasks: RemediationTask[];
  jobId: string;
  onRemediationTaskReplaced?: (task: RemediationTask) => void;
}

export function RemediationTaskBoard({
  tasks,
  jobId,
  onRemediationTaskReplaced,
}: RemediationTaskBoardProps): JSX.Element | null {
  if (!Array.isArray(tasks) || tasks.length === 0) {
    return null;
  }
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
            改善タスク（セクション別）
          </Typography>
          <Typography variant="caption" sx={{ color: "#64748b" }}>
            制作・見積りにそのまま貼れるよう、優先度とページ部位で整理しています。
          </Typography>
        </Stack>
      </Stack>
      {hasLockedRemediationTasks(tasks) && (
        <Alert severity="info" sx={{ mb: 2 }}>
          まずは基礎改修（Level 1）を完了させてください。AIからの認知度が上がると、より高度な戦略が解放されます。
        </Alert>
      )}
      <TaskCardGroup tasks={tasks} jobId={jobId} onTaskReplaced={onRemediationTaskReplaced} />
    </Box>
  );
}
