import BusinessIcon from "@mui/icons-material/Business";
import CloudUploadOutlinedIcon from "@mui/icons-material/CloudUploadOutlined";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import ShoppingBagIcon from "@mui/icons-material/ShoppingBag";
import StorefrontIcon from "@mui/icons-material/Storefront";
import WorkspacePremiumIcon from "@mui/icons-material/WorkspacePremium";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Container,
  FormControl,
  FormControlLabel,
  FormLabel,
  IconButton,
  Paper,
  Radio,
  RadioGroup,
  Snackbar,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { createJob, CreateJobHttpError } from "../api/jobsApi";
import {
  changeWorkspacePlan,
  fetchWorkspacePlan,
  type WorkspaceSubscriptionPlan,
} from "../api/workspace-api";
import { useBranding } from "../branding/useBranding";
import { LoadingCharacter } from "../components/LoadingCharacter";
import type { CompetitorExtractionMode } from "../types/createJobRequest";

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

const PLAN_LABELS: Record<WorkspaceSubscriptionPlan, string> = {
  STANDARD: "Standard",
  PRO: "Pro",
  EXPERT: "Expert",
};

const DEV_PLAN_OPTIONS: WorkspaceSubscriptionPlan[] = ["STANDARD", "PRO", "EXPERT"];

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

const INDUSTRY_OPTIONS: {
  value: CompetitorExtractionMode;
  title: string;
  description: string;
  Icon: typeof StorefrontIcon;
}[] = [
  {
    value: "LOCAL_STORE",
    title: "地域密着・店舗型",
    description: "来店・近隣集客が中心のクライアント向け",
    Icon: StorefrontIcon,
  },
  {
    value: "CORPORATE_SERVICE",
    title: "企業向け・専門サービス",
    description: "BtoB・受託・SaaS など全国の提案が中心",
    Icon: BusinessIcon,
  },
  {
    value: "ONLINE_SERVICE",
    title: "ネット通販・Webサービス",
    description: "EC・デジタル商品・宅配型などオンライン完結型",
    Icon: ShoppingBagIcon,
  },
];

export default function JobCreationPage(): JSX.Element {
  const navigate = useNavigate();
  const muiTheme = useTheme();
  const { toolName, logoBlobUrl } = useBranding();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [brandName, setBrandName] = useState("");
  const [industryType, setIndustryType] = useState<CompetitorExtractionMode>("LOCAL_STORE");
  const [targetUrl, setTargetUrl] = useState("");
  const [businessSummary, setBusinessSummary] = useState("");
  const [targetAudience, setTargetAudience] = useState("");
  const [focusPoints, setFocusPoints] = useState("");
  const [knowledgeFiles, setKnowledgeFiles] = useState<File[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [skipSnackbarOpen, setSkipSnackbarOpen] = useState(false);
  const [skipSnackbarMessage, setSkipSnackbarMessage] = useState("");
  const [plan, setPlan] = useState<WorkspaceSubscriptionPlan | null>(null);

  // プラン切替スイッチは開発環境専用（プランごとの機能ゲート検証用）。本番では実ユーザーに出さない。
  const devPlanSwitchEnabled = import.meta.env.DEV;

  useEffect(() => {
    if (!devPlanSwitchEnabled) {
      return;
    }
    let active = true;
    void fetchWorkspacePlan().then((p) => {
      if (active) {
        setPlan(p);
      }
    });
    return () => {
      active = false;
    };
  }, [devPlanSwitchEnabled]);

  const handleDevPlanChange = async (next: WorkspaceSubscriptionPlan | null) => {
    if (next === null || next === plan) {
      return;
    }
    const ok = await changeWorkspacePlan(next);
    if (ok) {
      // 切替後、プランゲートされた画面は各々の再取得時に新プランを反映する。
      setPlan(next);
    }
  };

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
          industryType,
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
    // 解析開始画面も結果画面と統一した「和の薄藤色」背景に（おもちゃ感を出さない低彩度）。
    <Box
      sx={{
        minHeight: "100vh",
        background: "linear-gradient(180deg, #f5f2fb 0%, #eae3f4 50%, #f2eef9 100%)",
      }}
    >
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 2 }}>
        {logoBlobUrl !== null ? (
          <Box component="img" src={logoBlobUrl} alt="" sx={{ height: 36, width: "auto", display: "block" }} />
        ) : null}
        <Typography variant="subtitle1" fontWeight={700}>
          {toolName}
        </Typography>
        {/* 核③ SaaSグロース: ログイン直後の最初の画面から Pro プランへ常時誘導する導線 */}
        <Stack direction="row" alignItems="center" spacing={1.5} sx={{ ml: "auto", flexShrink: 0 }}>
          {devPlanSwitchEnabled && plan !== null ? (
            // 開発専用: プランを実際に切り替えて機能ゲートを検証するためのスイッチ。本番ビルドでは描画されない。
            <Stack direction="row" alignItems="center" spacing={0.75}>
              <Typography
                variant="caption"
                sx={{ color: "warning.main", fontWeight: 700, letterSpacing: 0.5 }}
              >
                DEV
              </Typography>
              <ToggleButtonGroup
                size="small"
                exclusive
                value={plan}
                onChange={(_ev, next) => void handleDevPlanChange(next as WorkspaceSubscriptionPlan | null)}
                aria-label="開発用プラン切替"
              >
                {DEV_PLAN_OPTIONS.map((p) => (
                  <ToggleButton key={p} value={p} sx={{ py: 0.25, px: 1 }}>
                    {PLAN_LABELS[p]}
                  </ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Stack>
          ) : null}
          <Button
            variant="outlined"
            size="small"
            startIcon={<WorkspacePremiumIcon />}
            onClick={() => navigate("/pricing")}
          >
            プラン・料金
          </Button>
        </Stack>
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
            <FormControl component="fieldset" variant="standard" fullWidth disabled={submitting}>
              <FormLabel component="legend" sx={{ mb: 1, fontWeight: 600, color: "text.primary" }}>
                クライアントの事業タイプ
              </FormLabel>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                競合の探し方を最適化するために、エンドクライアントに近い形を選んでください。
              </Typography>
              <RadioGroup
                value={industryType}
                onChange={(_, v) => setIndustryType(v as CompetitorExtractionMode)}
              >
                <Stack spacing={1.5}>
                  {INDUSTRY_OPTIONS.map((opt) => {
                    const selected = industryType === opt.value;
                    const IconComp = opt.Icon;
                    return (
                      <Paper
                        key={opt.value}
                        variant="outlined"
                        sx={{
                          borderWidth: 2,
                          borderColor: selected ? "primary.main" : "divider",
                          bgcolor: selected ? "action.selected" : "background.paper",
                          transition: "border-color 0.15s, background-color 0.15s",
                        }}
                      >
                        <FormControlLabel
                          value={opt.value}
                          control={<Radio sx={{ alignSelf: "flex-start", mt: 0.5 }} />}
                          label={
                            <Stack direction="row" spacing={1.5} sx={{ py: 1, pr: 1, alignItems: "flex-start" }}>
                              <IconComp
                                color={selected ? "primary" : "action"}
                                sx={{ fontSize: 32, flexShrink: 0, mt: 0.25 }}
                              />
                              <Box>
                                <Typography variant="subtitle1" fontWeight={600}>
                                  {opt.title}
                                </Typography>
                                <Typography variant="body2" color="text.secondary">
                                  {opt.description}
                                </Typography>
                              </Box>
                            </Stack>
                          }
                          sx={{ m: 0, ml: 1, mr: 0, alignItems: "stretch", width: "100%" }}
                        />
                      </Paper>
                    );
                  })}
                </Stack>
              </RadioGroup>
            </FormControl>
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
    </Box>
  );
}
