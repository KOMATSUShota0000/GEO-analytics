import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useMemo } from "react";
import { groupTasksForDisplay } from "../../lib/taskUtils";
import type { RemediationTask } from "../../types/analysis";
import { TaskCard } from "./TaskCard";

export type TaskCardGroupProps = {
  tasks: RemediationTask[];
};

export function TaskCardGroup({ tasks }: TaskCardGroupProps): JSX.Element | null {
  const groups = useMemo(() => groupTasksForDisplay(tasks), [tasks]);
  if (groups.length === 0) {
    return null;
  }
  return (
    <Stack spacing={2.5}>
      {groups.map((g) => (
        <Paper
          key={g.priority}
          elevation={0}
          sx={{
            p: 2,
            borderRadius: "12px",
            border: "1px solid rgba(226,232,240,0.95)",
            backgroundColor: "#fafafa",
          }}
        >
          <Typography variant="subtitle1" sx={{ fontWeight: 800, color: "#0f172a" }}>
            {g.heading}
          </Typography>
          <Typography variant="body2" sx={{ color: "#64748b", mb: 1.5 }}>
            {g.note}
          </Typography>
          <Stack spacing={1.5}>
            {g.tasks.map(({ number, task }) => (
              <TaskCard key={task.id} task={task} number={number} />
            ))}
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}
