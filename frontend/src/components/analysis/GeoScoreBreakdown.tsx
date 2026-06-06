import { Box, Chip, LinearProgress, Stack, Typography } from "@mui/material";
import type { ScoreBreakdown } from "../../types/analysis";

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

export interface GeoScoreBreakdownProps {
  breakdown: ScoreBreakdown | null | undefined;
  brandName?: string;
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
  value: number;
  max: number;
  color: string;
}

function AxisRow({ label, value, max, color }: AxisRowProps): JSX.Element {
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
        </Stack>
        <Typography variant="body2" sx={{ fontVariantNumeric: "tabular-nums", color: "#0f172a" }}>
          <span style={{ fontWeight: 700 }}>{fmt(value)}</span>
          <span style={{ color: "#94a3b8", fontWeight: 400 }}> / {max}</span>
        </Typography>
      </Stack>
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

export function GeoScoreBreakdown({
  breakdown,
  brandName,
}: GeoScoreBreakdownProps): JSX.Element | null {
  if (!breakdown) {
    return null;
  }
  const total = clamp(breakdown.finalScore, 0, MAX_TOTAL);
  const core = breakdown.authorityThirdPartyCore;
  const localSub = breakdown.authorityLocalMeoSub;
  const bonus = breakdown.authorityWikipediaKgBonus;
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
              label="コンテンツ素地"
              value={breakdown.contentTotal}
              max={MAX_CONTENT}
              color={AXIS_CONTENT}
            />
            <AxisRow
              label="技術素地"
              value={breakdown.technicalTotal}
              max={MAX_TECHNICAL}
              color={AXIS_TECHNICAL}
            />
            <AxisRow
              label="権威・エンティティ認知"
              value={breakdown.authorityTotal}
              max={MAX_AUTHORITY}
              color={AXIS_AUTHORITY}
            />
            {core > 0 ? (
              <SubRow label="第三者言及の広がり" value={core} max={MAX_AUTHORITY_CORE} />
            ) : null}
            {localSub > 0 ? (
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
