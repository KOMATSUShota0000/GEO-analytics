import AddIcon from "@mui/icons-material/Add";
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  CssBaseline,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Stack,
  TextField,
  ThemeProvider,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  createTheme,
} from "@mui/material";
import type { MouseEvent } from "react";
import { useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { normalizeJobStatusResponse } from "../types/analysis";

const theme = createTheme();

function isThresholdError(message: string): boolean {
  return message.includes("キーワードの上限を超えています");
}

export default function JobCreationPage(): JSX.Element {
  const navigate = useNavigate();
  const [brandName, setBrandName] = useState("");
  const [keywordDraft, setKeywordDraft] = useState("");
  const [keywords, setKeywords] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [analysisMode, setAnalysisMode] = useState<"realtime" | "deep">("realtime");
  const [upsellOpen, setUpsellOpen] = useState(false);

  const handleAnalysisModeChange = (
    _event: MouseEvent<HTMLElement>,
    newValue: "realtime" | "deep" | null,
  ) => {
    if (newValue === null) {
      return;
    }
    if (newValue === "deep") {
      setUpsellOpen(true);
      return;
    }
    setAnalysisMode(newValue);
  };

  const addKeyword = () => {
    const trimmed = keywordDraft.trim();
    if (!trimmed) return;
    if (keywords.includes(trimmed)) {
      setKeywordDraft("");
      return;
    }
    setKeywords((prev) => [...prev, trimmed]);
    setKeywordDraft("");
  };

  const removeKeyword = (value: string) => {
    setKeywords((prev) => prev.filter((k) => k !== value));
  };

  const createJob = async () => {
    setError(null);
    const brand = brandName.trim();
    if (!brand) {
      setError("ブランド名を入力してください。");
      return;
    }
    setSubmitting(true);
    try {
      const createRes = await fetch("/api/v1/jobs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ brandName: brand }),
      });
      if (!createRes.ok) {
        const text = await createRes.text();
        throw new Error(text || `HTTP ${createRes.status}`);
      }
      const raw: unknown = await createRes.json();
      const created = normalizeJobStatusResponse(raw);
      if (created === null) {
        throw new Error("ジョブ作成レスポンスの形式が不正です");
      }
      const jobId = created.jobId;
      if (keywords.length > 0) {
        const queriesRes = await fetch(`/api/v1/jobs/${jobId}/queries`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ queries: keywords, plan: "STANDARD" }),
        });
        if (!queriesRes.ok) {
          const text = await queriesRes.text();
          let message = text || `HTTP ${queriesRes.status}`;
          try {
            const parsed: unknown = JSON.parse(text);
            if (
              typeof parsed === "object" &&
              parsed !== null &&
              "errorMessage" in parsed &&
              typeof (parsed as { errorMessage: unknown }).errorMessage === "string"
            ) {
              message = (parsed as { errorMessage: string }).errorMessage;
            }
          } catch {}
          throw new Error(message);
        }
      }
      navigate(`/job/${jobId}`);
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e);
      setError(message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="sm" sx={{ py: 4 }}>
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            gap: 2,
            mb: 3,
            flexWrap: "wrap",
          }}
        >
          <Box sx={{ flex: "1 1 auto", minWidth: 0 }}>
            <Typography variant="h4" component="h1" gutterBottom fontWeight={600}>
              ジョブ作成
            </Typography>
            <Typography color="text.secondary">
              ブランド名と解析したいキーワードを登録します。
            </Typography>
          </Box>
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "flex-end",
              flexShrink: 0,
            }}
          >
            <Typography
              variant="caption"
              color="text.secondary"
              fontWeight={600}
              sx={{ mb: 0.75, display: "block" }}
            >
              解析モード
            </Typography>
            <ToggleButtonGroup
              exclusive
              size="small"
              value={analysisMode}
              onChange={handleAnalysisModeChange}
              color="primary"
              sx={{
                gap: 0.75,
                "& .MuiToggleButtonGroup-grouped": {
                  border: 1,
                  borderColor: "divider",
                  borderRadius: "8px !important",
                  mx: 0,
                  px: 1.25,
                  fontWeight: 600,
                  textTransform: "none",
                  "&.Mui-selected": {
                    bgcolor: "primary.main",
                    color: "primary.contrastText",
                    borderColor: "primary.main",
                    "&:hover": {
                      bgcolor: "primary.dark",
                    },
                  },
                },
              }}
            >
              <ToggleButton value="realtime" sx={{ whiteSpace: "nowrap" }}>
                Realtime Mode
              </ToggleButton>
              <ToggleButton value="deep" sx={{ whiteSpace: "nowrap" }}>
                Deep Analysis Mode 💎
              </ToggleButton>
            </ToggleButtonGroup>
          </Box>
        </Box>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            <Stack spacing={0.5}>
              <Typography variant="body2" component="span">
                {error}
              </Typography>
              {isThresholdError(error) && (
                <Box sx={{ mt: 1.5 }}>
                  <Link
                    component={RouterLink}
                    to="/pricing"
                    variant="body2"
                    fontWeight={600}
                    underline="hover"
                  >
                    Proプランへのアップグレードはこちら
                  </Link>
                </Box>
              )}
            </Stack>
          </Alert>
        )}
        <Stack spacing={3}>
          <TextField
            label="ブランド名"
            value={brandName}
            onChange={(e) => setBrandName(e.target.value)}
            fullWidth
            required
            autoComplete="organization"
          />
          <Box>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1} alignItems="flex-start">
              <TextField
                label="キーワード"
                value={keywordDraft}
                onChange={(e) => setKeywordDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    addKeyword();
                  }
                }}
                fullWidth
                placeholder="キーワードを入力して追加"
              />
              <Button
                variant="outlined"
                startIcon={<AddIcon />}
                onClick={addKeyword}
                sx={{ flexShrink: 0, alignSelf: { xs: "stretch", sm: "center" } }}
              >
                追加
              </Button>
            </Stack>
            {keywords.length > 0 && (
              <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 2 }}>
                {keywords.map((k) => (
                  <Chip key={k} label={k} onDelete={() => removeKeyword(k)} />
                ))}
              </Stack>
            )}
          </Box>
          <Button
            variant="contained"
            size="large"
            onClick={createJob}
            disabled={submitting}
          >
            {submitting ? "作成中…" : "ジョブを作成"}
          </Button>
        </Stack>
        <Dialog open={upsellOpen} onClose={() => setUpsellOpen(false)} maxWidth="sm" fullWidth>
          <DialogTitle fontWeight={700}>Proプラン限定機能</DialogTitle>
          <DialogContent>
            <Typography variant="body1" color="text.secondary">
              Deep Analysis Modeによる大規模・一括解析は、Proプラン限定の機能です。
            </Typography>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2, gap: 1 }}>
            <Button onClick={() => setUpsellOpen(false)} color="inherit">
              閉じる
            </Button>
            <Button
              variant="contained"
              color="primary"
              component={RouterLink}
              to="/pricing"
              onClick={() => setUpsellOpen(false)}
            >
              アップグレードはこちら
            </Button>
          </DialogActions>
        </Dialog>
      </Container>
    </ThemeProvider>
  );
}
