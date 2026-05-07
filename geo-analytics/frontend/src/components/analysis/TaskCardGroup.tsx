import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useMemo } from "react";
import { groupTasksForDisplay } from "../../lib/taskUtils";
import type { RemediationTask } from "../../types/analysis";
import { TaskCard } from "./TaskCard";

export type TaskCardGroupProps = {
  tasks: RemediationTask[];
  jobId: string;
  onTaskReplaced?: (task: RemediationTask) => void;
};

export function TaskCardGroup({ tasks, jobId, onTaskReplaced }: TaskCardGroupProps): JSX.Element | null {
  const groups = useMemo(() => groupTasksForDisplay(tasks), [tasks]);
  if (groups.length === 0) {
    return null;
  }
  return (
    <Stack spacing={2.5}>
      {groups.map((g) => (
        <Paper
          key={g.sectionLabel}
          elevation={0}
          sx={{
            p: 2,
            borderRadius: "12px",
            border: "1px solid rgba(226,232,240,0.95)",
            backgroundColor: "#fafafa",
          }}
        >
          <Typography variant="subtitle1" sx={{ fontWeight: 800, color: "#0f172a", mb: 1.5 }}>
            {g.sectionLabel}
          </Typography>
          <Stack spacing={1.5}>
            {g.tasks.map((t) => (
              <TaskCard key={t.id} task={t} jobId={jobId} onTaskReplaced={onTaskReplaced} />
            ))}
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}
