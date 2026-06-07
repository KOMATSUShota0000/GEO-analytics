import { Box, Chip, Collapse, LinearProgress, Stack, Typography } from "@mui/material";
import { useState } from "react";
import type { ContentEvidenceItem, ScoreBreakdown } from "../../types/analysis";

// V13_GEO4AXIS の3軸配点（バックエンドと一致）。MEO単独軸は「権威・エンティティ認知」へ昇華済み。
const MAX_CONTENT = 50;
const MAX_TECHNICAL = 20;
const MAX_AUTHORITY = 30;
const MAX_TOTAL = 100;

// 権威軸(0-30)の内訳上限: 第三者言及の中核(0-20) + ローカル評判MEOサブ(0-10) + Wikipedia/KGボーナス(0-10)。
const MAX_AUTHORITY_CORE = 20;
const MAX_AUTHORITY_LOCAL_SUB = 10;
const MAX_AUTHORITY_BONUS = 10;

const AXIS_CONTENT = "#6366F1";
const AXIS_TECHNICAL = "#F59E0B";
const AXIS_AUTHORITY = "#10B981";

// ルーブリックLLM10項目の人間可読ラベル（criterionId→日本語）。バックエンドのenum名に対応。
const CRITERION_LABELS: Record<string, string> = {
  DIRECT_ANSWER_FIRST: "結論ファースト構成",
  ATOMIC_FACTS: "数値化された実績データ",
  SOLUTION_SCENARIOS: "導入事例・活用シーン",
  VERIFIABLE_AUTHORITY: "証明できる専門性",
  FAQ_PRESENCE: "FAQ（よくある質問）の記述",
  NUMBERED_PROCESS_FLOW: "番号付きの詳細な手順フロー",
  ENTITY_BIOGRAPHY: "具体的な経歴・バイオグラフィー",
  LOCAL_CONTEXT: "地域特有のコンテキスト",
  PRICE_AND_CONSTRAINTS: "詳細な料金体系と制約",
  EXTERNAL_CITATIONS: "外部ソースへの言及",
};

interface VerdictVisual {
  label: string;
  color: string;
  bg: string;
}
const VERDICT_VISUALS: Record<string, VerdictVisual> = {
  YES: { label: "満たす", color: "#047857", bg: "rgba(16,185,129,0.12)" },
  PARTIAL: { label: "一部", color: "#b45309", bg: "rgba(245,158,11,0.14)" },
  NO: { label: "なし", color: "#64748b", bg: "rgba(100,116,139,0.10)" },
};

export interface GeoScoreBreakdownProps {
  breakdown: ScoreBreakdown | null | undefined;
  brandName?: string;
  // コンテンツの充実度のサイト固有エビデンス（ルーブリック10項目・直接引用）。クリックで展開。
  contentEvidence?: ContentEvidenceItem[];
  // 「AIが読みやすい構造」軸のサイト固有エビデンス（Schema.org/H1/robots等の実クロール所見の要約文）。
  technicalEvidence?: string;
  // 業種モード（現行の3軸表示では再配分に使わないが、呼び出し側互換のため受け取る）。
  industryMode?: string;
}

function clamp(value: number, lo: number, hi: number): number {
  if (Number.isNaN(value) || !Number.isFinite(value)) {
    return lo;
  }
  return Math.max(lo, Math.min(hi, value));
}

function fmt(value: number): string {
  return clamp(value, 0, MAX_TOTAL).toFixed(1);
}

function ratio(value: number, max: number): number {
  if (max <= 0) {
    return 0;
  }
  return clamp((value / max) * 100, 0, 100);
}

interface AxisRowProps {
  label: string;
  // 各軸が何を測るのかを平易に説明する一文（専門用語を避け、提案先の非専門家にも伝わるように）。
  description: string;
  value: number;
  max: number;
  color: string;
  // 指定すると軸名の横に「根拠を見る」トグルを出し、クリックでエビデンスを開閉する。
  onToggle?: () => void;
  expanded?: boolean;
}

function AxisRow({ label, description, value, max, color, onToggle, expanded }: AxisRowProps): JSX.Element {
  const pct = ratio(value, max);
  return (
    <Stack spacing={0.5}>
      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Stack direction="row" spacing={1} alignItems="center">
          <Box
            sx={{
              width: 10,
              height: 10,
              borderRadius: "50%",
              backgroundColor: color,
              boxShadow: "0 0 0 2px rgba(15,23,42,0.04)",
            }}
          />
          <Typography variant="body2" sx={{ color: "#334155", fontWeight: 500 }}>
            {label}
          </Typography>
          {onToggle !== undefined ? (
            <Box
              component="button"
              type="button"
              onClick={onToggle}
              className="pdf-no-print"
              sx={{
                border: "1px solid rgba(99,102,241,0.3)",
                borderRadius: "999px",
                backgroundColor: "rgba(99,102,241,0.06)",
                cursor: "pointer",
                px: 1,
                py: "1px",
                color: "#6366F1",
                fontSize: 11,
                fontWeight: 700,
                lineHeight: 1.6,
              }}
            >
              {expanded === true ? "根拠を隠す ▴" : "根拠を見る ▾"}
            </Box>
          ) : null}
        </Stack>
        <Typography variant="body2" sx={{ fontVariantNumeric: "tabular-nums", color: "#0f172a" }}>
          <span style={{ fontWeight: 700 }}>{fmt(value)}</span>
          <span style={{ color: "#94a3b8", fontWeight: 400 }}> / {max}</span>
        </Typography>
      </Stack>
      <Typography variant="caption" sx={{ color: "#94a3b8", pl: 2.25, lineHeight: 1.4 }}>
        {description}
      </Typography>
      <LinearProgress
        variant="determinate"
        value={pct}
        sx={{
          height: 8,
          borderRadius: 999,
          backgroundColor: "rgba(148,163,184,0.18)",
          "& .MuiLinearProgress-bar": {
            backgroundColor: color,
            borderRadius: 999,
          },
        }}
      />
    </Stack>
  );
}

interface SubRowProps {
  label: string;
  value: number;
  max: number;
}

// 権威軸の内訳を控えめに見せる行（インデント・小さめ）。スコア本体ではなく構成の説明。
function SubRow({ label, value, max }: SubRowProps): JSX.Element {
  return (
    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ pl: 2.5 }}>
      <Typography variant="caption" sx={{ color: "#64748b" }}>
        └ {label}
      </Typography>
      <Typography variant="caption" sx={{ fontVariantNumeric: "tabular-nums", color: "#64748b" }}>
        {clamp(value, 0, max).toFixed(1)} / {max}
      </Typography>
    </Stack>
  );
}

// コンテンツの充実度のサイト固有エビデンス（ルーブリック10項目）。
// 各項目の判定＋「サイト本文からの直接引用」を見せ、「自分のサイトをちゃんと読んでいる」信頼を生む。
function ContentEvidencePanel({ items }: { items: ContentEvidenceItem[] }): JSX.Element {
  return (
    <Box sx={{ mt: 1, borderTop: "1px dashed rgba(148,163,184,0.45)", pt: 1.25 }}>
      <Typography variant="caption" sx={{ color: "#64748b" }}>
        あなたのサイトの実際の記述をAIが項目ごとに評価しました（「」内は原文からの引用）。
      </Typography>
      <Stack spacing={1} sx={{ mt: 1 }}>
        {items.map((it) => {
          const v = VERDICT_VISUALS[it.verdict] ?? VERDICT_VISUALS.NO;
          const label = CRITERION_LABELS[it.criterionId] ?? it.criterionId;
          const quote = it.evidence.trim();
          return (
            <Box
              key={it.criterionId}
              sx={{ borderRadius: "10px", border: "1px solid rgba(226,232,240,0.9)", padding: "10px 12px" }}
            >
              <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
                <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                  <Chip
                    size="small"
                    label={v.label}
                    sx={{ backgroundColor: v.bg, color: v.color, fontWeight: 700, height: 20 }}
                  />
                  <Typography variant="body2" sx={{ fontWeight: 600, color: "#334155" }}>
                    {label}
                  </Typography>
                </Stack>
                <Typography
                  variant="caption"
                  sx={{ color: "#94a3b8", fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}
                >
                  {clamp(it.score, 0, it.maxScore).toFixed(1)} / {it.maxScore}
                </Typography>
              </Stack>
              {quote.length > 0 ? (
                <Typography
                  variant="caption"
                  sx={{
                    display: "block",
                    mt: 0.75,
                    pl: 1,
                    borderLeft: "3px solid rgba(99,102,241,0.35)",
                    color: "#475569",
                    fontStyle: "italic",
                  }}
                >
                  「{quote}」
                </Typography>
              ) : (
                <Typography variant="caption" sx={{ display: "block", mt: 0.75, color: "#94a3b8" }}>
                  該当する記述がサイト本文に見つかりませんでした。
                </Typography>
              )}
            </Box>
          );
        })}
      </Stack>
    </Box>
  );
}

// 技術評価の要約文（"Schema.org: 未実装, H1欠落, ..." 等）を読みやすく項目分割する。
// バックエンドのクロール所見そのものなので、サイトごとに内容が異なる（定型文にならない）。
function TechnicalEvidencePanel({ summary }: { summary: string }): JSX.Element {
  const items = summary
    .split(/[,、]/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  return (
    <Box sx={{ mt: 1, borderTop: "1px dashed rgba(148,163,184,0.45)", pt: 1.25 }}>
      <Typography variant="caption" sx={{ color: "#64748b" }}>
        あなたのサイトを実際にクロールして得た、AI可読性の技術所見です。
      </Typography>
      {items.length > 0 ? (
        <Stack spacing={0.5} sx={{ mt: 1 }}>
          {items.map((it, i) => (
            <Stack key={`${i}-${it}`} direction="row" spacing={1} alignItems="flex-start">
              <Box
                sx={{ width: 6, height: 6, borderRadius: "50%", backgroundColor: AXIS_TECHNICAL, mt: "6px" }}
              />
              <Typography variant="body2" sx={{ color: "#334155" }}>
                {it}
              </Typography>
            </Stack>
          ))}
        </Stack>
      ) : (
        <Typography variant="body2" sx={{ color: "#475569", mt: 1, whiteSpace: "pre-wrap" }}>
          {summary}
        </Typography>
      )}
    </Box>
  );
}

export function GeoScoreBreakdown({
  breakdown,
  brandName,
  contentEvidence,
  technicalEvidence,
  industryMode,
}: GeoScoreBreakdownProps): JSX.Element | null {
  const [contentOpen, setContentOpen] = useState(false);
  const [technicalOpen, setTechnicalOpen] = useState(false);
  if (!breakdown) {
    return null;
  }
  const hasContentEvidence = Array.isArray(contentEvidence) && contentEvidence.length > 0;
  const hasTechnicalEvidence =
    typeof technicalEvidence === "string" && technicalEvidence.trim().length > 0;
  const total = clamp(breakdown.finalScore, 0, MAX_TOTAL);
  const core = breakdown.authorityThirdPartyCore;
  const localSub = breakdown.authorityLocalMeoSub;
  const bonus = breakdown.authorityWikipediaKgBonus;
  // 非地域業種(BtoB/SaaS/EC)はローカルMEO（クチコミ）を持たない。代わりに中核＝第三者言及が
  // 権威軸0-30を単独で構成する（BE: authorityThirdPartyCore が業種で0-20→0-30に拡張）。
  const isNonLocal = industryMode === "CORPORATE_SERVICE" || industryMode === "ONLINE_SERVICE";
  const coreMax = isNonLocal ? MAX_AUTHORITY : MAX_AUTHORITY_CORE;
  return (
    <Box
      sx={{
        borderRadius: "16px",
        border: "1px solid rgba(226,232,240,0.9)",
        background: "linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)",
        boxShadow: "0 8px 30px rgba(15,23,42,0.06)",
        padding: "20px 24px",
      }}
    >
      <Stack direction={{ xs: "column", md: "row" }} spacing={3} alignItems={{ md: "center" }}>
        <Box sx={{ minWidth: 180 }}>
          <Typography variant="caption" sx={{ color: "#64748b", letterSpacing: 0.4 }}>
            GEO Readiness Score
          </Typography>
          <Stack direction="row" alignItems="baseline" spacing={0.5} sx={{ mt: 0.5 }}>
            <Typography
              sx={{
                fontSize: 44,
                lineHeight: 1.0,
                fontWeight: 800,
                color: "var(--brand-color, #4f46e5)",
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {total.toFixed(1)}
            </Typography>
            <Typography sx={{ color: "#94a3b8", fontWeight: 600 }}>/{MAX_TOTAL}</Typography>
          </Stack>
          {brandName !== undefined && brandName.length > 0 ? (
            <Chip
              size="small"
              label={brandName}
              sx={{
                mt: 1,
                backgroundColor: "rgba(99,102,241,0.08)",
                color: "var(--brand-color, #4f46e5)",
                fontWeight: 600,
              }}
            />
          ) : null}
        </Box>
        <Box sx={{ flex: 1, minWidth: 0, width: "100%" }}>
          <Stack spacing={1.25}>
            <AxisRow
              label="コンテンツの充実度"
              description="AIが引用したくなる情報の質・量"
              value={breakdown.contentTotal}
              max={MAX_CONTENT}
              color={AXIS_CONTENT}
              onToggle={hasContentEvidence ? () => setContentOpen((o) => !o) : undefined}
              expanded={contentOpen}
            />
            {hasContentEvidence ? (
              <Collapse in={contentOpen} unmountOnExit>
                <ContentEvidencePanel items={contentEvidence ?? []} />
              </Collapse>
            ) : null}
            <AxisRow
              label="AIが読みやすい構造"
              description="Schema.org等でAIが正しく解釈できるか"
              value={breakdown.technicalTotal}
              max={MAX_TECHNICAL}
              color={AXIS_TECHNICAL}
              onToggle={hasTechnicalEvidence ? () => setTechnicalOpen((o) => !o) : undefined}
              expanded={technicalOpen}
            />
            {hasTechnicalEvidence ? (
              <Collapse in={technicalOpen} unmountOnExit>
                <TechnicalEvidencePanel summary={technicalEvidence ?? ""} />
              </Collapse>
            ) : null}
            <AxisRow
              label="第三者からの評判・権威"
              description="他サイト・口コミでどれだけ言及されるか"
              value={breakdown.authorityTotal}
              max={MAX_AUTHORITY}
              color={AXIS_AUTHORITY}
            />
            {core > 0 ? (
              <SubRow label="第三者言及の広がり" value={core} max={coreMax} />
            ) : null}
            {!isNonLocal && localSub > 0 ? (
              <SubRow label="ローカル評判（クチコミ）" value={localSub} max={MAX_AUTHORITY_LOCAL_SUB} />
            ) : null}
            {bonus > 0 ? (
              <SubRow label="Wikipedia / ナレッジグラフ" value={bonus} max={MAX_AUTHORITY_BONUS} />
            ) : null}
          </Stack>
        </Box>
      </Stack>
    </Box>
  );
}
