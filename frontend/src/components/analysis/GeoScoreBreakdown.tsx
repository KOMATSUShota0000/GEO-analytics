import { Box, Chip, LinearProgress, Stack, Typography } from "@mui/material";
import type { ScoreBreakdown } from "../../types/analysis";

const MAX_AI_AUDIT = 50;
const MAX_MEO = 25;
const MAX_MACHINE_READABILITY = 25;
const MAX_TOTAL = 100;

// 非地域業種（全国BtoB・オンライン）は MEO を除外し AI×1.2／機械×1.6 へ再配分する（ADR-019 / バックエンドと一致）。
const AI_WEIGHT_NON_LOCAL = 1.2;
const MACHINE_WEIGHT_NON_LOCAL = 1.6;
const MAX_AI_AUDIT_NON_LOCAL = MAX_AI_AUDIT * AI_WEIGHT_NON_LOCAL; // 60
const MAX_MACHINE_NON_LOCAL = MAX_MACHINE_READABILITY * MACHINE_WEIGHT_NON_LOCAL; // 40

const AXIS_AI = "#6366F1";
const AXIS_MEO = "#10B981";
const AXIS_MR = "#F59E0B";

function isNonLocalIndustry(mode: string | undefined): boolean {
  return mode === "CORPORATE_SERVICE" || mode === "ONLINE_SERVICE";
}

export interface GeoScoreBreakdownProps {
  breakdown: ScoreBreakdown | null | undefined;
  brandName?: string;
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

export function GeoScoreBreakdown({
  breakdown,
  brandName,
  industryMode,
}: GeoScoreBreakdownProps): JSX.Element | null {
  if (!breakdown) {
    return null;
  }
  const total = clamp(breakdown.finalScore, 0, MAX_TOTAL);
  const nonLocal = isNonLocalIndustry(industryMode);
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
            {nonLocal ? (
              <>
                <AxisRow
                  label="AI 監査"
                  value={breakdown.aiAuditTotal * AI_WEIGHT_NON_LOCAL}
                  max={MAX_AI_AUDIT_NON_LOCAL}
                  color={AXIS_AI}
                />
                <AxisRow
                  label="機械可読性"
                  value={breakdown.machineReadabilityTotal * MACHINE_WEIGHT_NON_LOCAL}
                  max={MAX_MACHINE_NON_LOCAL}
                  color={AXIS_MR}
                />
              </>
            ) : (
              <>
                <AxisRow label="AI 監査" value={breakdown.aiAuditTotal} max={MAX_AI_AUDIT} color={AXIS_AI} />
                <AxisRow label="MEO トラスト" value={breakdown.meoTotal} max={MAX_MEO} color={AXIS_MEO} />
                <AxisRow
                  label="機械可読性"
                  value={breakdown.machineReadabilityTotal}
                  max={MAX_MACHINE_READABILITY}
                  color={AXIS_MR}
                />
              </>
            )}
          </Stack>
        </Box>
      </Stack>
    </Box>
  );
}
