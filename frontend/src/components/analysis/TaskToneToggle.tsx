import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Snackbar from "@mui/material/Snackbar";
import Stack from "@mui/material/Stack";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import { type SyntheticEvent, useState } from "react";
import { apiFetch, responseJsonAsCamel } from "../../api/apiFetch";
import {
  parseTaskToneRegenerateEnvelope,
  type RemediationTask,
  type RemediationTaskTone,
} from "../../types/analysis";

export type TaskToneToggleProps = {
  jobId: string;
  task: RemediationTask;
  onTaskReplaced: (next: RemediationTask) => void;
  onBusyChange: (busy: boolean) => void;
};

export function TaskToneToggle({
  jobId,
  task,
  onTaskReplaced,
  onBusyChange,
}: TaskToneToggleProps): JSX.Element | null {
  const [tone, setTone] = useState<RemediationTaskTone>("PROFESSIONAL");
  const [loading, setLoading] = useState(false);
  const [rateLocked, setRateLocked] = useState(false);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");

  const masked = task.isMasked === true;

  const handleToneChange = (_: SyntheticEvent, value: RemediationTaskTone | null): void => {
    if (value !== null) {
      setTone(value);
    }
  };

  const handleRegenerate = (): void => {
    if (masked || rateLocked || loading) {
      return;
    }
    setLoading(true);
    onBusyChange(true);
    void (async () => {
      try {
        const res = await apiFetch(`/api/v1/jobs/${jobId}/tasks/${task.id}/regenerate`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ tone }),
        });
        if (res.status === 429) {
          setRateLocked(true);
          setSnackbarMessage("制限に達しました。1分後に再度お試しください");
          setSnackbarOpen(true);
          window.setTimeout(() => {
            setRateLocked(false);
          }, 60000);
          return;
        }
        if (!res.ok) {
          setSnackbarMessage("エラーが発生しました。時間をおいて再度お試しください");
          setSnackbarOpen(true);
          return;
        }
        const raw: unknown = await responseJsonAsCamel(res);
        const next = parseTaskToneRegenerateEnvelope(raw);
        if (next !== null) {
          onTaskReplaced(next);
        }
      } catch {
        setSnackbarMessage("エラーが発生しました。時間をおいて再度お試しください");
        setSnackbarOpen(true);
      } finally {
        setLoading(false);
        onBusyChange(false);
      }
    })();
  };

  if (masked) {
    return null;
  }

  return (
    <Stack direction="row" alignItems="center" flexWrap="wrap" spacing={1} useFlexGap sx={{ mt: 0.5 }}>
      <ToggleButtonGroup
        exclusive
        size="small"
        value={tone}
        onChange={handleToneChange}
        disabled={loading || rateLocked}
        aria-label="トーン"
      >
        <ToggleButton value="PROFESSIONAL" sx={{ textTransform: "none", fontSize: 12 }}>
          プロ
        </ToggleButton>
        <ToggleButton value="FRIENDLY" sx={{ textTransform: "none", fontSize: 12 }}>
          フレンドリー
        </ToggleButton>
        <ToggleButton value="AGGRESSIVE" sx={{ textTransform: "none", fontSize: 12 }}>
          強調
        </ToggleButton>
      </ToggleButtonGroup>
      <Button
        variant="outlined"
        size="small"
        disabled={loading || rateLocked}
        onClick={handleRegenerate}
        sx={{ textTransform: "none" }}
      >
        {loading ? <CircularProgress color="inherit" size={18} /> : "再生成"}
      </Button>
      <Snackbar
        open={snackbarOpen}
        onClose={(): void => {
          setSnackbarOpen(false);
        }}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        autoHideDuration={8000}
        message={snackbarMessage}
      />
    </Stack>
  );
}
