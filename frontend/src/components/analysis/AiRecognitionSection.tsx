import { Box, Chip, Collapse, Link, Stack, Typography } from "@mui/material";
import { useState } from "react";
import type { AiRecognitionState, AiRecognitionSummary } from "../../types/analysis";

// 各ステートの定性表現。色はステートのセマンティクス（肯定=緑/注意=橙/未認知=灰）で、
// ブランドカラーには依存させない（スコアではなく状態の説明のため）。
interface StateVisual {
  chipLabel: string;
  headline: string;
  description: string;
  accent: string;
  bg: string;
}

const STATE_VISUALS: Record<AiRecognitionState, StateVisual> = {
  RECOGNIZED_CORRECTLY: {
    chipLabel: "正しく認識",
    headline: "AIは現在あなたを正しい実体として認識しています",
    description:
      "生成AIはブランドを正確な実体として捉えています。この認識を維持し、第三者言及で確固たるものにしましょう。",
    accent: "#10B981",
    bg: "rgba(16,185,129,0.08)",
  },
  MISIDENTIFIED: {
    chipLabel: "取り違えあり",
    headline: "AIがあなたを別の実体と取り違えています",
    description:
      "同名他社やハルシネーションによる誤認が起きています。構造化データでエンティティを明確化し、信頼できる第三者言及を増やすことで是正を狙います。",
    accent: "#F59E0B",
    bg: "rgba(245,158,11,0.10)",
  },
  UNKNOWN: {
    chipLabel: "未認識",
    headline: "AIはまだあなたを認識していません",
    description:
      "生成AIが実体を解決できていません。権威・エンティティ認知（第三者言及・構造化データ）の強化が最優先の打ち手です。",
    accent: "#64748B",
    bg: "rgba(100,116,139,0.08)",
  },
};

export interface AiRecognitionSectionProps {
  summary: AiRecognitionSummary | null | undefined;
  // この解析で実際に使用したクエリ一覧。クリックで開示し、解析の透明性（多角的に測った証跡）を示す。
  queries?: string[];
}

interface CountChipProps {
  label: string;
  count: number;
  color: string;
}

function CountChip({ label, count, color }: CountChipProps): JSX.Element {
  return (
    <Stack direction="row" spacing={0.75} alignItems="center">
      <Box sx={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: color }} />
      <Typography variant="caption" sx={{ color: "#475569" }}>
        {label}
      </Typography>
      <Typography
        variant="caption"
        sx={{ fontWeight: 700, color: "#0f172a", fontVariantNumeric: "tabular-nums" }}
      >
        {count}
      </Typography>
    </Stack>
  );
}

export function AiRecognitionSection({
  summary,
  queries,
}: AiRecognitionSectionProps): JSX.Element | null {
  const [showQueries, setShowQueries] = useState(false);
  // 評価対象クエリが無ければ何も表示しない（旧データ・未評価ジョブの過渡期対策）。
  if (!summary || summary.evaluatedCount <= 0) {
    return null;
  }
  const hasQueries = queries != null && queries.length > 0;
  const v = STATE_VISUALS[summary.dominant];
  // dominantが肯定でも一部クエリで取り違え/未認識があれば件数でニュアンスを補足する。
  const nuance =
    summary.dominant === "RECOGNIZED_CORRECTLY" && summary.misidentifiedCount > 0
      ? `ただし ${summary.misidentifiedCount} 件のクエリでは別実体との取り違えが見られます。`
      : summary.dominant === "UNKNOWN" && summary.recognizedCount > 0
        ? `一部（${summary.recognizedCount} 件）では正しく認識されています。`
        : null;
  return (
    <Box
      sx={{
        borderRadius: "16px",
        border: "1px solid rgba(226,232,240,0.9)",
        borderLeft: `4px solid ${v.accent}`,
        background: "#ffffff",
        boxShadow: "0 8px 30px rgba(15,23,42,0.06)",
        padding: "20px 24px",
      }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
        <Typography variant="caption" sx={{ color: "#64748b", letterSpacing: 0.4 }}>
          AI ブランド認識状況
        </Typography>
        <Chip
          size="small"
          label="スコア非算入・定性エビデンス"
          sx={{ backgroundColor: "rgba(100,116,139,0.08)", color: "#64748b", fontWeight: 500 }}
        />
      </Stack>
      <Box sx={{ borderRadius: "12px", backgroundColor: v.bg, padding: "14px 16px", mb: 1.5 }}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip
            size="small"
            label={v.chipLabel}
            sx={{ backgroundColor: v.accent, color: "#ffffff", fontWeight: 700 }}
          />
          <Typography variant="subtitle1" sx={{ fontWeight: 700, color: "#0f172a" }}>
            {v.headline}
          </Typography>
        </Stack>
        <Typography variant="body2" sx={{ color: "#334155", mt: 1 }}>
          {v.description}
        </Typography>
        {nuance !== null ? (
          <Typography variant="body2" sx={{ color: v.accent, fontWeight: 600, mt: 0.75 }}>
            {nuance}
          </Typography>
        ) : null}
      </Box>
      <Stack
        direction="row"
        spacing={2.5}
        alignItems="center"
        flexWrap="wrap"
        useFlexGap
        sx={{ rowGap: 1 }}
      >
        <CountChip label="正しく認識" count={summary.recognizedCount} color="#10B981" />
        <CountChip label="取り違え" count={summary.misidentifiedCount} color="#F59E0B" />
        <CountChip label="未認識" count={summary.unknownCount} color="#94A3B8" />
        {hasQueries ? (
          <Link
            component="button"
            type="button"
            variant="caption"
            underline="hover"
            onClick={() => setShowQueries((prev) => !prev)}
            sx={{ color: "#64748b", ml: "auto", fontWeight: 600 }}
          >
            評価 {summary.evaluatedCount} クエリ {showQueries ? "▲" : "▼"}
          </Link>
        ) : (
          <Typography variant="caption" sx={{ color: "#94a3b8", ml: "auto" }}>
            評価 {summary.evaluatedCount} クエリ
          </Typography>
        )}
      </Stack>
      {hasQueries ? (
        <Collapse in={showQueries}>
          <Box sx={{ mt: 1.5, pt: 1.5, borderTop: "1px dashed rgba(148,163,184,0.45)" }}>
            <Typography variant="caption" sx={{ color: "#64748b", fontWeight: 600 }}>
              この解析で使用したクエリ
            </Typography>
            <Box component="ol" sx={{ mt: 0.75, mb: 0, pl: 2.5 }}>
              {queries!.map((q, i) => (
                <Typography
                  component="li"
                  key={`${i}-${q}`}
                  variant="body2"
                  sx={{ color: "#334155", mb: 0.25 }}
                >
                  {q}
                </Typography>
              ))}
            </Box>
          </Box>
        </Collapse>
      ) : null}
    </Box>
  );
}
