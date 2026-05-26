import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";

export function OperationUpsellBanner(): JSX.Element {
  return (
    <Paper
      elevation={0}
      sx={{
        p: 2.5,
        borderRadius: "14px",
        border: "1px solid rgba(139,92,246,0.35)",
        backgroundImage: "linear-gradient(125deg, rgba(238,242,255,0.95) 0%, rgba(250,245,255,0.98) 45%, rgba(224,231,255,0.92) 100%)",
        boxShadow: "0 10px 40px rgba(79,70,229,0.12)",
      }}
    >
      <Stack spacing={2} alignItems="flex-start">
        <Typography sx={{ fontSize: 15, lineHeight: 1.65, color: "#1e1b4b", fontWeight: 600 }}>
          基礎改修が完了しました。ここからAIの推奨順位を維持・向上させるには、継続的な情報更新と口コミの獲得（運用）が必須です。
        </Typography>
        <Button variant="contained" component="a" href="#inquiry" sx={{ textTransform: "none", fontWeight: 700 }}>
          運用代行プランを相談する
        </Button>
      </Stack>
    </Paper>
  );
}
