import AutoAwesomeOutlinedIcon from "@mui/icons-material/AutoAwesomeOutlined";
import Alert, { type AlertColor } from "@mui/material/Alert";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import useMediaQuery from "@mui/material/useMediaQuery";
import { keyframes } from "@mui/material/styles";
import type { EmotionalAlertPayload } from "../types/analysis";

const dangerShake = keyframes`
  0%, 100% { transform: translateX(0); }
  15% { transform: translateX(-3px); }
  30% { transform: translateX(3px); }
  45% { transform: translateX(-2px); }
  60% { transform: translateX(2px); }
  75% { transform: translateX(-1px); }
  90% { transform: translateX(1px); }
`;

function levelToAlertSeverity(level: EmotionalAlertPayload["level"]): AlertColor {
  switch (level) {
    case "DANGER":
      return "error";
    case "WARNING":
      return "warning";
    default:
      return "info";
  }
}

export type EmotionalAlertBannerProps = {
  payload: EmotionalAlertPayload;
};

export function EmotionalAlertBanner({ payload }: EmotionalAlertBannerProps): JSX.Element {
  const prefersReducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)", {
    defaultMatches: false,
  });
  const severity = levelToAlertSeverity(payload.level);
  const animateDanger = payload.level === "DANGER" && !prefersReducedMotion;
  return (
    <Alert
      severity={severity}
      variant="filled"
      icon={<AutoAwesomeOutlinedIcon fontSize="inherit" />}
      sx={
        animateDanger
          ? {
              animation: `${dangerShake} 0.65s ease-in-out 1`,
              "@media (prefers-reduced-motion: reduce)": {
                animation: "none",
              },
            }
          : undefined
      }
    >
      <Stack spacing={0.5}>
        <Typography variant="body2" component="span">
          {payload.message}
        </Typography>
        {payload.usedFallback ? (
          <Typography variant="caption" component="p" sx={{ opacity: 0.85, mb: 0 }}>
            AIが事実に基づき自動構成しました
          </Typography>
        ) : null}
      </Stack>
    </Alert>
  );
}
