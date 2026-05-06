import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import type { EmotionalAlert } from "../lib/buildEmotionalAlerts";

export type EmotionalAlertBannerProps = {
  alerts: EmotionalAlert[];
};

export function EmotionalAlertBanner({ alerts }: EmotionalAlertBannerProps): JSX.Element | null {
  if (alerts.length === 0) {
    return null;
  }
  return (
    <Stack spacing={1} className="pdf-avoid-break mb-4">
      {alerts.map((a, idx) => (
        <Alert
          key={`${idx}-${a.message.slice(0, 40)}`}
          severity={a.severity}
          variant="filled"
          action={
            <Button
              color="inherit"
              size="small"
              onClick={() =>
                document.getElementById("next-action-section")?.scrollIntoView({ behavior: "smooth" })
              }
            >
              今すぐ対策を見る
            </Button>
          }
        >
          {a.message}
        </Alert>
      ))}
    </Stack>
  );
}
