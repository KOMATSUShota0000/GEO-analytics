import AddIcon from "@mui/icons-material/Add";
import {
  Alert,
  Box,
  Button,
  Chip,
  Container,
  CssBaseline,
  Stack,
  TextField,
  ThemeProvider,
  Typography,
  createTheme,
} from "@mui/material";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { normalizeJobStatusResponse } from "../types/analysis";

const theme = createTheme();

export default function JobCreationPage(): JSX.Element {
  const navigate = useNavigate();
  const [brandName, setBrandName] = useState("");
  const [keywordDraft, setKeywordDraft] = useState("");
  const [keywords, setKeywords] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

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
          body: JSON.stringify({ queries: keywords }),
        });
        if (!queriesRes.ok) {
          const text = await queriesRes.text();
          throw new Error(text || `HTTP ${queriesRes.status}`);
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
        <Typography variant="h4" component="h1" gutterBottom fontWeight={600}>
          ジョブ作成
        </Typography>
        <Typography color="text.secondary" sx={{ mb: 3 }}>
          ブランド名と解析したいキーワードを登録します。
        </Typography>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
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
      </Container>
    </ThemeProvider>
  );
}
