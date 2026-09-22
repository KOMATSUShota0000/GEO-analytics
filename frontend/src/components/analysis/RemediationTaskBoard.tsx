import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { hasLockedRemediationTasks } from "../../lib/taskUtils";
import type { RemediationTask } from "../../types/analysis";
import { TaskCardGroup } from "./TaskCardGroup";

export interface RemediationTaskBoardProps {
  tasks: RemediationTask[];
}

export function RemediationTaskBoard({ tasks }: RemediationTaskBoardProps): JSX.Element | null {
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
            改善タスク
          </Typography>
          <Typography variant="caption" sx={{ color: "#64748b" }}>
            番号の小さいものから順に進めてください。効果の大きいものから、同じ効果の中ではすぐ直せるものから並べています。「効果」は、AIの回答で取り上げられやすくなる度合いの目安です。
          </Typography>
        </Stack>
      </Stack>
      {hasLockedRemediationTasks(tasks) && (
        <Alert severity="info" sx={{ mb: 2 }}>
          効果「大」の対策は、具体的な手順と根拠を Pro プラン以上で表示します。タイトルと「なぜ効くか」はそのままご覧いただけます。
        </Alert>
      )}
      <TaskCardGroup tasks={tasks} />
    </Box>
  );
}
