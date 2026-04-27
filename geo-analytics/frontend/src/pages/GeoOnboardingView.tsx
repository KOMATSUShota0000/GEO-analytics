import {
  Alert,
  Backdrop,
  Box,
  Button,
  CircularProgress,
  Container,
  CssBaseline,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  TextField,
  ThemeProvider,
  Typography,
  createTheme,
} from "@mui/material";
import { useCallback, useState, type Dispatch, type SetStateAction } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  parseApiErrorBody,
  patchProjectContext,
  postExtractContext,
} from "../api/geoOnboardingApi";

const theme = createTheme();
const industries: { value: string; label: string }[] = [
  { value: "YMYL", label: "YMYL分野" },
  { value: "LOCAL", label: "ローカルビジネス" },
  { value: "B2B", label: "法人向け" },
  { value: "B2C", label: "消費者向け" },
  { value: "EC", label: "通販・EC" },
  { value: "OTHER", label: "その他" },
];

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

export default function GeoOnboardingView(): JSX.Element {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [url, setUrl] = useState("");
  const [extracting, setExtracting] = useState(false);
  const [industryType, setIndustryType] = useState<string>("OTHER");
  const [strengthsText, setStrengthsText] = useState("");
  const [targetAudience, setTargetAudience] = useState("");
  const [hasPreview, setHasPreview] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldKey, string>>>({});
  const [toast, setToast] = useState<string | null>(null);

  const onExtract = useCallback(async () => {
    if (!projectId) {
      return;
    }
    setError(null);
    setFieldErrors({});
    setExtracting(true);
    try {
      const data = await postExtractContext(projectId, url);
      setIndustryType(data.industryType);
      setStrengthsText(data.strengths.join("\n"));
      setTargetAudience(data.targetAudience);
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
      setExtracting(false);
    }
  }, [projectId, url]);

  const onSave = useCallback(async () => {
    if (!projectId) {
      return;
    }
    setError(null);
    setFieldErrors({});
    setSaving(true);
    try {
      await patchProjectContext(projectId, {
        industryType,
        extractedStrengths: strengthsText,
        targetAudience,
      });
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
  }, [projectId, industryType, strengthsText, targetAudience]);

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
          自社サイトのGEO分析
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          URLを入力し、AIに強みとターゲットを抽出します。内容を確認し、保存で確定します。
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
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
        {hasPreview && (
          <Box className="rounded-lg border border-slate-200/90 p-4" sx={{ borderColor: "divider" }}>
            <Typography variant="h6" sx={{ mb: 2 }} fontWeight={600}>
              プレビューと編集
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
              <Button
                variant="outlined"
                onClick={() => void onSave()}
                disabled={saving}
                color="primary"
              >
                保存
              </Button>
            </Stack>
          </Box>
        )}
        <Button sx={{ mt: 2 }} onClick={() => navigate(`/projects/${projectId}/settings`)}>
          設定へ
        </Button>
        <Backdrop open={extracting} sx={{ color: "#fff", zIndex: (t) => t.zIndex.drawer + 1 }}>
          <Stack alignItems="center" gap={1}>
            <CircularProgress color="inherit" />
            <Typography>AIが解析中です…</Typography>
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
