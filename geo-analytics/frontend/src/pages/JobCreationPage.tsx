import AddIcon from "@mui/icons-material/Add";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Container,
  CssBaseline,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Snackbar,
  Stack,
  TextField,
  ThemeProvider,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
  createTheme,
} from "@mui/material";
import type { MouseEvent } from "react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import { convertProposalToJob } from "../api/queryProposalApi";
import { apiFetch, parseJsonTextAsCamel, responseJsonAsCamel } from "../api/apiFetch";
import { AIStrategyProposalWizard } from "../components/AIStrategyProposalWizard";
import { LoadingCharacter } from "../components/LoadingCharacter";
import { extractApiErrorMessage, normalizeJobStatusResponse } from "../types/analysis";

const theme = createTheme();

const CONVERT_NAVIGATE_DELAY_MS = 600;

/** 解析開始フロー: 握り潰し禁止（コンソール + アラート + 呼び出し元で UI 表示用メッセージ返却） */
function exposeAndFormatJobApiError(context: string, e: unknown): string {
  console.error(`[JobCreationPage:${context}]`, e);
  const message =
    e instanceof Error ? e.message : typeof e === "string" ? e : `不明なエラー: ${String(e)}`;
  window.alert(`エラーが発生しました: ${message}`);
  return message;
}

function isThresholdError(message: string): boolean {
  return (
    message.includes("キーワードの上限を超えています") ||
    message.includes("クエリの上限を超えています")
  );
}

export default function JobCreationPage(): JSX.Element {
  const navigate = useNavigate();
  const navigateTimeoutRef = useRef<number | null>(null);
  const [brandName, setBrandName] = useState("");
  const [queryDraft, setQueryDraft] = useState("");
  const [queries, setQueries] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [analysisMode, setAnalysisMode] = useState<"realtime" | "deep">("realtime");
  const [upsellOpen, setUpsellOpen] = useState(false);
  const [wizardBusy, setWizardBusy] = useState(false);
  const [convertSuccessOpen, setConvertSuccessOpen] = useState(false);

  useEffect(() => {
    return () => {
      if (navigateTimeoutRef.current !== null) {
        window.clearTimeout(navigateTimeoutRef.current);
      }
    };
  }, []);

  const handleWizardBusyChange = useCallback((busy: boolean) => {
    setWizardBusy(busy);
  }, []);

  const handleProposalComplete = async (_queries: string[], proposalId: string): Promise<void> => {
    const jobId = await convertProposalToJob(proposalId, "STANDARD");
    setConvertSuccessOpen(true);
    if (navigateTimeoutRef.current !== null) {
      window.clearTimeout(navigateTimeoutRef.current);
    }
    navigateTimeoutRef.current = window.setTimeout(() => {
      navigateTimeoutRef.current = null;
      navigate(`/job/${jobId}`);
    }, CONVERT_NAVIGATE_DELAY_MS);
  };

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

  const addQuery = () => {
    const trimmed = queryDraft.trim();
    if (!trimmed) return;
    if (queries.includes(trimmed)) {
      setQueryDraft("");
      return;
    }
    setQueries((prev) => [...prev, trimmed]);
    setQueryDraft("");
  };

  const removeQuery = (value: string) => {
    setQueries((prev) => prev.filter((q) => q !== value));
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
      const createRes = await apiFetch("/api/v1/jobs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ brandName: brand }),
      });
      if (!createRes.ok) {
        const text = await createRes.text();
        throw new Error(text || `HTTP ${createRes.status}`);
      }
      const raw: unknown = await responseJsonAsCamel(createRes);
      const created = normalizeJobStatusResponse(raw);
      if (created === null) {
        throw new Error("ジョブ作成レスポンスの形式が不正です");
      }
      const jobId = created.jobId;
      if (queries.length > 0) {
        const queriesRes = await apiFetch(`/api/v1/jobs/${jobId}/queries`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ queries, plan: "STANDARD" }),
        });
        if (!queriesRes.ok) {
          const text = await queriesRes.text();
          let message = text || `HTTP ${queriesRes.status}`;
          try {
            const parsed: unknown = parseJsonTextAsCamel(text);
            const em = extractApiErrorMessage(parsed);
            if (em !== undefined) {
              message = em;
            }
          } catch {
            /* use message as-is */
          }
          throw new Error(message);
        }
      }
      navigate(`/job/${jobId}`);
    } catch (e: unknown) {
      setError(exposeAndFormatJobApiError("createJob", e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="md" sx={{ py: 4 }}>
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
              AIによるクエリ提案と手動入力のどちらでも、解析ジョブを開始できます。
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
        <Card variant="outlined" sx={{ mb: 3 }}>
          <CardHeader
            title="AI戦略クエリ提案"
            titleTypographyProps={{ variant: "h6", component: "h2", fontWeight: 600 }}
          />
          <CardContent>
            <AIStrategyProposalWizard
              disabled={submitting}
              onWizardBusyChange={handleWizardBusyChange}
              onProposalComplete={handleProposalComplete}
            />
          </CardContent>
        </Card>

        <Card variant="outlined">
          <CardHeader
            title="手動でジョブを作成"
            titleTypographyProps={{ variant: "h6", component: "h2", fontWeight: 600 }}
          />
          <CardContent>
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
                    label="クエリ"
                    value={queryDraft}
                    onChange={(e) => setQueryDraft(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        addQuery();
                      }
                    }}
                    fullWidth
                    placeholder="クエリを入力して追加"
                  />
                  <Button
                    variant="outlined"
                    startIcon={<AddIcon />}
                    onClick={addQuery}
                    sx={{ flexShrink: 0, alignSelf: { xs: "stretch", sm: "center" } }}
                  >
                    追加
                  </Button>
                </Stack>
                {queries.length > 0 && (
                  <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 2 }}>
                    {queries.map((q) => (
                      <Chip key={q} label={q} onDelete={() => removeQuery(q)} />
                    ))}
                  </Stack>
                )}
              </Box>
              {submitting && (
                <Box sx={{ mb: 1 }}>
                  <LoadingCharacter />
                </Box>
              )}
              <Button
                variant="contained"
                size="large"
                onClick={createJob}
                disabled={submitting || wizardBusy}
                sx={
                  submitting
                    ? {
                        py: 1.5,
                        background: "linear-gradient(135deg, #4f46e5 0%, #7c3aed 50%, #0284c7 100%)",
                        color: "#fff",
                        boxShadow: "0 8px 24px rgba(79, 70, 229, 0.35)",
                        "&.Mui-disabled": {
                          color: "rgba(255,255,255,0.95)",
                          opacity: 1,
                          background: "linear-gradient(135deg, #4f46e5 0%, #7c3aed 50%, #0284c7 100%)",
                        },
                      }
                    : { py: 1.5 }
                }
              >
                {submitting ? "作成中…" : "ジョブを作成"}
              </Button>
            </Stack>
          </CardContent>
        </Card>

        <Snackbar
          open={convertSuccessOpen}
          autoHideDuration={4000}
          onClose={(_, reason) => {
            if (reason === "clickaway") {
              return;
            }
            setConvertSuccessOpen(false);
          }}
          anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        >
          <Alert
            severity="success"
            variant="filled"
            sx={{ width: "100%" }}
            onClose={() => setConvertSuccessOpen(false)}
          >
            分析ジョブを開始しました。解析画面へ移動します。
          </Alert>
        </Snackbar>

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
