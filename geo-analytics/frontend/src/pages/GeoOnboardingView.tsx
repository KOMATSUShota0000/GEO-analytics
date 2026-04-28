import {
  Alert,
  Backdrop,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Container,
  CssBaseline,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Snackbar,
  Stack,
  TextField,
  ThemeProvider,
  Typography,
  createTheme,
} from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { useCallback, useState, type Dispatch, type SetStateAction } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  type MinorityReportItem,
  parseApiErrorBody,
  patchProjectContext,
  postExtractContext,
} from "../api/geoOnboardingApi";
import { OnboardingMathVisualizer } from "../components/onboarding/OnboardingMathVisualizer";
import { OnboardingNarrationMonitor } from "../components/onboarding/OnboardingNarrationMonitor";
import { useOnboardingStream } from "../hooks/useOnboardingStream";

const theme = createTheme();
const industries: { value: string; label: string }[] = [
  { value: "YMYL", label: "YMYL分野" },
  { value: "LOCAL", label: "ローカルビジネス" },
  { value: "B2B", label: "法人向け" },
  { value: "B2C", label: "消費者向け" },
  { value: "EC", label: "通販・EC" },
  { value: "OTHER", label: "その他" },
];

const MAX_MINORITY = 10;

type FieldKey = "url" | "industryType" | "extractedStrengths" | "targetAudience";

function applyFieldErrors(
  err: { fields: Record<string, string> | undefined },
  setFieldErrors: Dispatch<SetStateAction<Partial<Record<FieldKey, string>>>>,
) {
  const f = err.fields;
  if (!f) {
    return;
  }
  const next: Partial<Record<FieldKey, string>> = {};
  for (const k of Object.keys(f)) {
    if (k === "url" || k === "industryType" || k === "extractedStrengths" || k === "targetAudience") {
      const v = f[k];
      if (typeof v === "string" && v.length > 0) {
        next[k] = v;
      }
    }
  }
  setFieldErrors(next);
}

function emptyMinorityRow(): MinorityReportItem {
  return { insight: "", conflictReason: "", evidence: "" };
}

export default function GeoOnboardingView(): JSX.Element {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [url, setUrl] = useState("");
  const [extracting, setExtracting] = useState(false);
  const [industryType, setIndustryType] = useState<string>("OTHER");
  const [strengthsText, setStrengthsText] = useState("");
  const [targetAudience, setTargetAudience] = useState("");
  const [minorityReports, setMinorityReports] = useState<MinorityReportItem[]>([]);
  const [hasPreview, setHasPreview] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldKey, string>>>({});
  const [toast, setToast] = useState<string | null>(null);

  const {
    startStream,
    stopStream,
    narrationLog,
    scoreSeries,
    latestRadar,
    streamError: streamChannelError,
    settlementNotice,
    dismissStreamAlerts,
  } = useOnboardingStream();

  const patchMinority = useCallback(
    (index: number, patch: Partial<MinorityReportItem>) => {
      setMinorityReports((prev) =>
        prev.map((row, i) => (i === index ? { ...row, ...patch } : row)),
      );
    },
    [],
  );

  const onExtract = useCallback(async () => {
    if (!projectId) {
      return;
    }
    setError(null);
    setFieldErrors({});
    setExtracting(true);
    const sessionId = crypto.randomUUID();
    try {
      await startStream(projectId, sessionId);
      const data = await postExtractContext(projectId, url, sessionId);
      setIndustryType(data.industryType);
      setStrengthsText(data.strengths.join("\n"));
      setTargetAudience(data.targetAudience);
      setMinorityReports(data.minorityReports.map((m) => ({ ...m })));
      setHasPreview(true);
    } catch (e: unknown) {
      if (
        e !== null
        && typeof e === "object"
        && "status" in e
        && (e as { status: number }).status === 400
        && "body" in e
        && typeof (e as { body: unknown }).body === "string"
      ) {
        const parsed = parseApiErrorBody((e as { body: string }).body);
        if (parsed) {
          setError(parsed.message);
          applyFieldErrors({ fields: parsed.fields }, setFieldErrors);
          return;
        }
      }
      if (e instanceof Error && e.name === "AbortError") {
        setError("解析は1分以内に完了しませんでした。URLを確認し、再試行してください。");
        return;
      }
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      stopStream();
      setExtracting(false);
    }
  }, [projectId, url, startStream, stopStream]);

  const onSave = useCallback(async () => {
    if (!projectId) {
      return;
    }
    setError(null);
    setFieldErrors({});
    setSaving(true);
    try {
      const data = await patchProjectContext(projectId, {
        industryType,
        extractedStrengths: strengthsText,
        targetAudience,
        minorityReports,
      });
      setIndustryType(data.industryType);
      setStrengthsText(data.strengths.join("\n"));
      setTargetAudience(data.targetAudience);
      setMinorityReports(data.minorityReports.map((m) => ({ ...m })));
      setToast("保存しました");
    } catch (e: unknown) {
      if (
        e !== null
        && typeof e === "object"
        && "status" in e
        && (e as { status: number }).status === 400
        && "body" in e
        && typeof (e as { body: unknown }).body === "string"
      ) {
        const parsed = parseApiErrorBody((e as { body: string }).body);
        if (parsed) {
          setError(parsed.message);
          applyFieldErrors({ fields: parsed.fields }, setFieldErrors);
          return;
        }
      }
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  }, [projectId, industryType, strengthsText, targetAudience, minorityReports]);

  if (!projectId) {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Container>
          <Typography>プロジェクトIDが不正です</Typography>
        </Container>
      </ThemeProvider>
    );
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Container maxWidth="md" className="py-8">
        <Typography variant="h4" component="h1" fontWeight={600} gutterBottom>
          GEOオンボーディング
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          URLを入力すると、AI検索推奨率に効く構造と独自性を4つのペルソナがレビューしながら要約します。内容を確認し、保存で確定します。
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        {streamChannelError !== null && streamChannelError.trim().length > 0 && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={dismissStreamAlerts}>
            {streamChannelError}
          </Alert>
        )}
        {settlementNotice !== null && settlementNotice.trim().length > 0 && (
          <Alert severity="warning" sx={{ mb: 2 }} onClose={dismissStreamAlerts}>
            {settlementNotice}
          </Alert>
        )}
        <Stack spacing={2} className="mb-6">
          <TextField
            label="WebサイトのURL"
            value={url}
            onChange={(valueEvent) => setUrl(valueEvent.target.value)}
            fullWidth
            required
            error={!!fieldErrors.url}
            helperText={fieldErrors.url}
          />
          <Button variant="contained" onClick={() => void onExtract()} disabled={extracting || url.trim().length === 0}>
            AI解析を開始
          </Button>
        </Stack>
        {(extracting
          || narrationLog.length > 0
          || scoreSeries.length > 0) && (
          <Paper
            elevation={0}
            variant="outlined"
            sx={{ p: 2.5, mb: 3, borderColor: "divider" }}
          >
            <Stack
              direction={{ xs: "column", md: "row" }}
              spacing={2}
              alignItems="flex-start"
            >
              <Box sx={{ flex: 1, minWidth: 0, width: "100%" }}>
                <OnboardingNarrationMonitor entries={narrationLog} />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0, width: "100%" }}>
                <OnboardingMathVisualizer
                  scoreSeries={scoreSeries}
                  pSiteRadar={latestRadar}
                />
              </Box>
            </Stack>
          </Paper>
        )}
        {hasPreview && (
          <Stack spacing={3}>
            <Paper
              elevation={0}
              variant="outlined"
              sx={{
                p: 2.5,
                borderLeft: 4,
                borderColor: "primary.main",
                bgcolor: "action.hover",
              }}
            >
              <Typography variant="overline" color="primary" fontWeight={700} letterSpacing={0.08}>
                盤石な合意案
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                事実根拠が重なり、スケプティックの批判を乗り越えた強みとターゲットを編集できます。
              </Typography>
              <Stack spacing={2}>
                <FormControl fullWidth>
                  <InputLabel id="industry-label">業種</InputLabel>
                  <Select
                    labelId="industry-label"
                    label="業種"
                    value={industryType}
                    onChange={(selectEvent) => setIndustryType(String(selectEvent.target.value))}
                    error={!!fieldErrors.industryType}
                  >
                    {industries.map((row) => (
                      <MenuItem key={row.value} value={row.value}>
                        {row.label}
                      </MenuItem>
                    ))}
                  </Select>
                  {fieldErrors.industryType && (
                    <Typography variant="caption" color="error" sx={{ mt: 0.5 }} component="p">
                      {fieldErrors.industryType}
                    </Typography>
                  )}
                </FormControl>
                <TextField
                  label="強み"
                  value={strengthsText}
                  onChange={(valueEvent) => setStrengthsText(valueEvent.target.value)}
                  fullWidth
                  multiline
                  minRows={4}
                  error={!!fieldErrors.extractedStrengths}
                  helperText={fieldErrors.extractedStrengths}
                />
                <TextField
                  label="ターゲット層"
                  value={targetAudience}
                  onChange={(valueEvent) => setTargetAudience(valueEvent.target.value)}
                  fullWidth
                  multiline
                  minRows={3}
                  error={!!fieldErrors.targetAudience}
                  helperText={fieldErrors.targetAudience}
                />
              </Stack>
            </Paper>

            <Paper
              elevation={0}
              variant="outlined"
              sx={{
                p: 2.5,
                borderLeft: 4,
                borderColor: "warning.main",
              }}
            >
              <Typography variant="overline" color="warning.dark" fontWeight={700} letterSpacing={0.08}>
                破壊的マイノリティ・レポート
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                多数派の合意に入らなかったが、根拠ある尖り。AI間の知的な摩擦をここに集約します。
              </Typography>
              <Stack spacing={2}>
                {minorityReports.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    まだマイノリティ行がありません。「行を追加」で編集するか、再度AI解析を試してください。
                  </Typography>
                )}
                {minorityReports.map((row, index) => (
                  <Card key={index} variant="outlined" sx={{ overflow: "visible" }}>
                    <CardContent sx={{ "&:last-child": { pb: 2 } }}>
                      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                        <Typography variant="subtitle2" color="text.secondary">
                          レポート {index + 1} / {MAX_MINORITY}
                        </Typography>
                        <IconButton
                          size="small"
                          aria-label="このレポートを削除"
                          onClick={() =>
                            setMinorityReports((prev) => prev.filter((_, i) => i !== index))
                          }
                        >
                          <DeleteOutlineIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                      <Stack spacing={1.5}>
                        <Box>
                          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                            <span aria-hidden>💡</span> 独自の強み（insight）
                          </Typography>
                          <TextField
                            value={row.insight}
                            onChange={(e) => patchMinority(index, { insight: e.target.value })}
                            fullWidth
                            multiline
                            minRows={2}
                            size="small"
                            placeholder="引用価値のある切り口"
                          />
                        </Box>
                        <Box>
                          <Typography variant="caption" color="warning.main" display="block" fontWeight={600} sx={{ mb: 0.5 }}>
                            <span aria-hidden>⚠️</span> 多数派の懸念（conflict）
                          </Typography>
                          <TextField
                            value={row.conflictReason}
                            onChange={(e) => patchMinority(index, { conflictReason: e.target.value })}
                            fullWidth
                            multiline
                            minRows={2}
                            size="small"
                            placeholder="合意が難しい理由"
                          />
                        </Box>
                        <Box>
                          <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                            <span aria-hidden>🔍</span> 根拠（evidence）
                          </Typography>
                          <TextField
                            value={row.evidence}
                            onChange={(e) => patchMinority(index, { evidence: e.target.value })}
                            fullWidth
                            multiline
                            minRows={2}
                            size="small"
                            placeholder="原文に基づく根拠"
                          />
                        </Box>
                      </Stack>
                    </CardContent>
                  </Card>
                ))}
                <Button
                  variant="outlined"
                  color="warning"
                  disabled={minorityReports.length >= MAX_MINORITY}
                  onClick={() => setMinorityReports((prev) => [...prev, emptyMinorityRow()])}
                >
                  行を追加（最大{MAX_MINORITY}件）
                </Button>
              </Stack>
            </Paper>

            <Button
              variant="contained"
              onClick={() => void onSave()}
              disabled={saving}
              color="primary"
            >
              保存
            </Button>
          </Stack>
        )}
        <Button sx={{ mt: 2 }} onClick={() => navigate(`/projects/${projectId}/settings`)}>
          設定へ
        </Button>
        <Backdrop open={extracting} sx={{ color: "#fff", zIndex: (t) => t.zIndex.drawer + 1 }}>
          <Stack alignItems="center" gap={1}>
            <CircularProgress color="inherit" />
            <Typography variant="body2" sx={{ maxWidth: 360, textAlign: "center" }}>
              4ペルソナのディベートを実況中です。構造的シグナルと独自性スコアをリアルタイムで表示しています。
            </Typography>
          </Stack>
        </Backdrop>
        <Snackbar
          open={toast !== null}
          autoHideDuration={4000}
          onClose={() => setToast(null)}
          message={toast ?? ""}
        />
      </Container>
    </ThemeProvider>
  );
}
