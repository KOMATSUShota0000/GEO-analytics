import { Box, Stack, Typography } from "@mui/material";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  PolarAngleAxis,
  PolarGrid,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { OnboardingScorePoint } from "../../types/onboardingDebateStream";

const RADAR_AXES = ["根拠シグナル", "オファリング明示", "読者接続", "補助基盤"] as const;

export interface OnboardingMathVisualizerProps {
  scoreSeries: OnboardingScorePoint[];
  pSiteRadar: number[] | null;
}

export function OnboardingMathVisualizer(props: OnboardingMathVisualizerProps): JSX.Element {
  const { scoreSeries, pSiteRadar } = props;

  const lineData = scoreSeries.map((p) => ({
    roundLabel: `R${p.round}`,
    round: p.round,
    独自性スコア: Number(p.geoIg.toFixed(5)),
  }));

  const radarData =
    pSiteRadar !== null && pSiteRadar.length >= 4
      ? RADAR_AXES.map((axis, i) => ({
          axis,
          構造バランス: Math.min(1, Math.max(0, pSiteRadar[i] ?? 0)),
        }))
      : RADAR_AXES.map((axis, i) => ({
          axis,
          構造バランス:
            pSiteRadar !== null && pSiteRadar[i] !== undefined
              ? Math.min(1, Math.max(0, pSiteRadar[i]))
              : 0,
        }));

  const radarHasSignal =
    pSiteRadar !== null
    && pSiteRadar.length > 0
    && pSiteRadar.some((v) => typeof v === "number" && v > 1e-8);

  return (
    <Stack spacing={2}>
      <Typography variant="subtitle2" color="text.secondary" fontWeight={700}>
        数理ビジュアライザー
      </Typography>
      <Box sx={{ height: 260, width: "100%" }}>
        {lineData.length === 0 ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 6, textAlign: "center" }}>
            スコア更新を待っています…
          </Typography>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={lineData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" opacity={0.35} />
              <XAxis dataKey="roundLabel" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} width={48} domain={["auto", "auto"]} />
              <Tooltip
                formatter={(value: number, name: string) => [
                  value,
                  name === "独自性スコア" ? "独自性スコア（情報の独自性）" : name,
                ]}
                labelFormatter={(l) => `ラウンド ${String(l)}`}
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="独自性スコア"
                stroke="#4f46e5"
                strokeWidth={2}
                dot={{ r: 3 }}
                isAnimationActive
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </Box>
      <Box sx={{ height: 300, width: "100%" }}>
        {!radarHasSignal ? (
          <Typography variant="body2" color="text.secondary" sx={{ py: 6, textAlign: "center" }}>
            構造バランス（サイト上の質量配分）はスコア更新とともに表示されます。
          </Typography>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <RadarChart data={radarData} cx="50%" cy="50%" outerRadius="78%">
              <PolarGrid />
              <PolarAngleAxis dataKey="axis" tick={{ fontSize: 11 }} />
              <Radar
                name="構造バランス"
                dataKey="構造バランス"
                stroke="#0d9488"
                fill="#14b8a6"
                fillOpacity={0.35}
                isAnimationActive
              />
              <Tooltip formatter={(v: number) => [v.toFixed(4), "質量シェア（正規化）"]} />
              <Legend />
            </RadarChart>
          </ResponsiveContainer>
        )}
      </Box>
    </Stack>
  );
}
