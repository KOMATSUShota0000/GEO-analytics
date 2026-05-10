import CloudUploadOutlinedIcon from "@mui/icons-material/CloudUploadOutlined";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Container,
  IconButton,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createJob, CreateJobHttpError } from "../api/jobsApi";
import { useBranding } from "../branding/useBranding";
import { LoadingCharacter } from "../components/LoadingCharacter";

const MAX_KNOWLEDGE_FILE_BYTES = 10 * 1024 * 1024;
const ALLOWED_KNOWLEDGE_EXTENSIONS = [".pdf", ".docx", ".txt", ".csv"] as const;

type KnowledgeFilePartition = {
  accepted: File[];
  rejectedOversize: number;
  rejectedExtension: number;
};

function partitionKnowledgeFiles(files: Iterable<File>): KnowledgeFilePartition {
  const accepted: File[] = [];
  let rejectedOversize = 0;
  let rejectedExtension = 0;
  for (const file of files) {
    if (file.size > MAX_KNOWLEDGE_FILE_BYTES) {
      rejectedOversize++;
      continue;
    }
    const lower = file.name.toLowerCase();
    const ok = ALLOWED_KNOWLEDGE_EXTENSIONS.some((ext) => lower.endsWith(ext));
    if (!ok) {
      rejectedExtension++;
      continue;
    }
    accepted.push(file);
  }
  return { accepted, rejectedOversize, rejectedExtension };
}

function buildKnowledgeSkipMessage(partition: KnowledgeFilePartition): string {
  const parts: string[] = [];
  if (partition.rejectedOversize > 0) {
    parts.push(`${partition.rejectedOversize}件は10MBを超えたため追加できませんでした`);
  }
  if (partition.rejectedExtension > 0) {
    parts.push(
      `${partition.rejectedExtension}件は許可形式（.pdf / .docx / .txt / .csv）以外のため追加できませんでした`,
    );
  }
  return parts.join("。") + "。";
}

function formatKnowledgeFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const kb = bytes / 1024;
  if (kb < 1024) {
    return `${kb < 10 ? kb.toFixed(1) : Math.round(kb)} KB`;
  }
  const mb = kb / 1024;
  return `${mb < 10 ? mb.toFixed(1) : Math.round(mb)} MB`;
}

const JOB_CREATE_TIMEOUT_MS = 7 * 60 * 1000;

const MSG_PAYLOAD_TOO_LARGE =
  "アップロード可能なサイズを超えています。1ファイルあたり最大10MB、リクエスト全体は50MB以内にしてください。ファイルを減らすか容量を小さくしてから再度お試しください。";

const MSG_TIMEOUT =
  "通信がタイムアウトしました。ネットワークとファイルサイズを確認し、再度お試しください。";

function formatJobCreateFailure(e: unknown): string {
  if (e instanceof DOMException && e.name === "AbortError") {
    return MSG_TIMEOUT;
  }
  if (e instanceof Error && e.name === "AbortError") {
    return MSG_TIMEOUT;
  }
  if (e instanceof CreateJobHttpError) {
    if (e.status === 413 || e.errorCode === "payload_too_large") {
      return MSG_PAYLOAD_TOO_LARGE;
    }
    return e.message.length > 0 ? e.message : `リクエストに失敗しました（HTTP ${e.status}）`;
  }
  if (e instanceof Error) {
    return e.message.length > 0 ? e.message : "予期しないエラーが発生しました。";
  }
  return `予期しないエラー: ${String(e)}`;
}

export default function JobCreationPage(): JSX.Element {
  const navigate = useNavigate();
  const muiTheme = useTheme();
  const { toolName, logoBlobUrl } = useBranding();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [brandName, setBrandName] = useState("");
  const [targetUrl, setTargetUrl] = useState("");
  const [businessSummary, setBusinessSummary] = useState("");
  const [targetAudience, setTargetAudience] = useState("");
  const [focusPoints, setFocusPoints] = useState("");
  const [knowledgeFiles, setKnowledgeFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [skipSnackbarOpen, setSkipSnackbarOpen] = useState(false);
  const [skipSnackbarMessage, setSkipSnackbarMessage] = useState("");

  const mergeIncomingFiles = (list: FileList | File[]) => {
    const arr = Array.from(list);
    const partition = partitionKnowledgeFiles(arr);
    if (partition.accepted.length > 0) {
      setKnowledgeFiles((prev) => [...prev, ...partition.accepted]);
    }
    if (partition.rejectedOversize > 0 || partition.rejectedExtension > 0) {
      setSkipSnackbarMessage(buildKnowledgeSkipMessage(partition));
      setSkipSnackbarOpen(true);
    }
  };

  const handleSubmit = async () => {
    if (submitting) {
      return;
    }
    setError(null);
    const bn = brandName.trim();
    const tu = targetUrl.trim();
    if (!bn) {
      setError("屋号・ブランド名を入力してください。");
      return;
    }
    if (!tu) {
      setError("解析対象URLを入力してください。");
      return;
    }
    setSubmitting(true);
    const controller = new AbortController();
    const timeoutId = window.setTimeout(() => controller.abort(), JOB_CREATE_TIMEOUT_MS);
    try {
      const created = await createJob(
        {
          brandName: bn,
          targetUrl: tu,
          businessSummary,
          targetAudience,
          focusPoints,
          files: knowledgeFiles.length > 0 ? knowledgeFiles : undefined,
        },
        controller.signal,
      );
      navigate(`/job/${created.jobId}`);
    } catch (e: unknown) {
      console.error("[JobCreationPage:createJob]", e);
      setError(formatJobCreateFailure(e));
    } finally {
      window.clearTimeout(timeoutId);
      setSubmitting(false);
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 2 }}>
        {logoBlobUrl !== null ? (
          <Box component="img" src={logoBlobUrl} alt="" sx={{ height: 36, width: "auto", display: "block" }} />
        ) : null}
        <Typography variant="subtitle1" fontWeight={700}>
          {toolName}
        </Typography>
      </Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" component="h1" gutterBottom fontWeight={600}>
          ジョブ作成
        </Typography>
        <Typography color="text.secondary">
          解析対象のURLとコンテキスト（任意）を入力して、GEO解析ジョブを開始できます。
        </Typography>
      </Box>
      {error ? (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          <Typography variant="body2" component="span">
            {error}
          </Typography>
        </Alert>
      ) : null}
      <Card variant="outlined">
        <CardHeader
          title="GEOコンテキスト"
          titleTypographyProps={{ variant: "h6", component: "h2", fontWeight: 600 }}
        />
        <CardContent>
          <Stack spacing={3}>
            <TextField
              label="屋号・ブランド名"
              value={brandName}
              onChange={(ev) => setBrandName(ev.target.value)}
              fullWidth
              required
              autoComplete="organization"
              inputProps={{ maxLength: 255 }}
              disabled={submitting}
            />
            <TextField
              label="解析対象URL"
              value={targetUrl}
              onChange={(ev) => setTargetUrl(ev.target.value)}
              fullWidth
              required
              type="url"
              placeholder="https://example.com"
              inputProps={{ maxLength: 2048 }}
              disabled={submitting}
            />
            <Box>
              <Typography variant="subtitle2" fontWeight={600} gutterBottom>
                参考資料（任意）
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                PDF・Word・テキスト・CSVをドラッグするか、枠内をクリックして選択できます。
              </Typography>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept=".pdf,.docx,.txt,.csv"
                style={{ display: "none" }}
                disabled={submitting}
                onChange={(ev) => {
                  const list = ev.target.files;
                  if (list !== null && list.length > 0) {
                    mergeIncomingFiles(list);
                  }
                  ev.target.value = "";
                }}
              />
              <Box
                onClick={() => {
                  if (!submitting) {
                    fileInputRef.current?.click();
                  }
                }}
                onDragOver={(ev) => {
                  ev.preventDefault();
                  ev.stopPropagation();
                }}
                onDrop={(ev) => {
                  ev.preventDefault();
                  ev.stopPropagation();
                  if (submitting) {
                    return;
                  }
                  const dt = ev.dataTransfer.files;
                  if (dt !== null && dt.length > 0) {
                    mergeIncomingFiles(dt);
                  }
                }}
                sx={{
                  borderStyle: "dashed",
                  borderWidth: 2,
                  borderColor: "divider",
                  borderRadius: 2,
                  p: 2.5,
                  textAlign: "center",
                  cursor: submitting ? "default" : "pointer",
                  bgcolor: submitting ? "action.disabledBackground" : "action.hover",
                  transition: "background-color 0.2s",
                  pointerEvents: submitting ? "none" : "auto",
                }}
              >
                <CloudUploadOutlinedIcon sx={{ fontSize: 40, color: "primary.main", mb: 0.5 }} />
                <Typography variant="body2" fontWeight={500}>
                  ここにファイルをドロップ
                </Typography>
                <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 0.5 }}>
                  最大10MB・.pdf / .docx / .txt / .csv
                </Typography>
              </Box>
              {knowledgeFiles.length > 0 ? (
                <Stack spacing={1} sx={{ mt: 2 }}>
                  {knowledgeFiles.map((f, index) => (
                    <Box
                      key={`${f.name}-${String(f.lastModified)}-${String(f.size)}-${String(index)}`}
                      sx={{
                        display: "flex",
                        alignItems: "center",
                        gap: 1,
                        py: 0.75,
                        px: 1.5,
                        borderRadius: 1,
                        bgcolor: "action.selected",
                        border: 1,
                        borderColor: "divider",
                      }}
                    >
                      <Typography variant="body2" sx={{ flex: 1, minWidth: 0 }} noWrap title={f.name}>
                        {f.name}
                      </Typography>
                      <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0 }}>
                        {formatKnowledgeFileSize(f.size)}
                      </Typography>
                      <IconButton
                        size="small"
                        aria-label="remove file"
                        disabled={submitting}
                        onClick={(ev) => {
                          ev.stopPropagation();
                          setKnowledgeFiles((prev) => prev.filter((_, i) => i !== index));
                        }}
                      >
                        <DeleteOutlineIcon fontSize="small" />
                      </IconButton>
                    </Box>
                  ))}
                </Stack>
              ) : null}
            </Box>
            <TextField
              label="事業概要"
              value={businessSummary}
              onChange={(ev) => setBusinessSummary(ev.target.value)}
              fullWidth
              multiline
              minRows={3}
              inputProps={{ maxLength: 16000 }}
              disabled={submitting}
            />
            <TextField
              label="想定顧客"
              value={targetAudience}
              onChange={(ev) => setTargetAudience(ev.target.value)}
              fullWidth
              multiline
              minRows={3}
              inputProps={{ maxLength: 16000 }}
              disabled={submitting}
            />
            <TextField
              label="重点課題・今後の戦略"
              value={focusPoints}
              onChange={(ev) => setFocusPoints(ev.target.value)}
              fullWidth
              multiline
              minRows={5}
              placeholder="例：現在は20代向けサロンとして認知されていますが、今後は30代フォーマルを取り込みつつオンライン予約強化したい／AI回答では〇〇という差別化を伝えてほしい、など。"
              helperText="💡ヒント：まだサイトに書いていない経営方針・優先順位でも構いません。AIが本文生成やクエリ選定で参照します。"
              inputProps={{ maxLength: 16000 }}
              disabled={submitting}
            />
            {submitting ? (
              <Box sx={{ mb: 1 }}>
                <LoadingCharacter />
              </Box>
            ) : null}
            <Button
              variant="contained"
              size="large"
              onClick={handleSubmit}
              disabled={submitting}
              sx={
                submitting
                  ? {
                      py: 1.5,
                      background: `linear-gradient(135deg, ${muiTheme.palette.primary.main} 0%, #7c3aed 50%, #0284c7 100%)`,
                      color: "#fff",
                      boxShadow: `0 8px 24px ${muiTheme.palette.primary.main}59`,
                      "&.Mui-disabled": {
                        color: "rgba(255,255,255,0.95)",
                        opacity: 1,
                        background: `linear-gradient(135deg, ${muiTheme.palette.primary.main} 0%, #7c3aed 50%, #0284c7 100%)`,
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
        open={skipSnackbarOpen}
        autoHideDuration={8000}
        onClose={() => setSkipSnackbarOpen(false)}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      >
        <Alert severity="warning" variant="filled" onClose={() => setSkipSnackbarOpen(false)} sx={{ width: "100%" }}>
          {skipSnackbarMessage}
        </Alert>
      </Snackbar>
    </Container>
  );
}
