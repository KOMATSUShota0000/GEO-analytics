import { useCallback, useEffect, useState } from "react";
import { apiFetch, responseJsonAsCamel } from "../api/apiFetch";

export type AssetSnapshotChartPoint = {
  snapshotDate: string;
  geoReadinessScore: number;
  localTrustCount: number;
  // 採点ロジック版（V13_GEO4AXIS / V12_PWIM 等）。4a-3でBE露出。経時グラフの切替点描画に使う。
  calculationVersion: string | null;
};

export type UseProjectAssetSnapshotsResult = {
  data: AssetSnapshotChartPoint[];
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
};

export function useProjectAssetSnapshots(
  projectId: string,
  from: string,
  to: string,
): UseProjectAssetSnapshotsResult {
  const [data, setData] = useState<AssetSnapshotChartPoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const pid = projectId.trim();
    if (pid.length === 0) {
      setData([]);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const url = `/api/v1/projects/${encodeURIComponent(pid)}/asset-snapshots?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
      const response = await apiFetch(url);
      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
      }
      const body = (await responseJsonAsCamel(response)) as { data?: unknown };
      const raw = body.data;
      const list = Array.isArray(raw) ? raw : [];
      const next: AssetSnapshotChartPoint[] = list.map((row: unknown) => {
        const o = row as Record<string, unknown>;
        return {
          snapshotDate: String(o.snapshotDate ?? ""),
          geoReadinessScore: Number(o.geoReadinessScore ?? 0),
          localTrustCount: Number(o.localTrustCount ?? 0),
          calculationVersion:
            typeof o.calculationVersion === "string" ? o.calculationVersion : null,
        };
      });
      setData(next);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : String(err);
      setError(message);
      setData([]);
    } finally {
      setLoading(false);
    }
  }, [projectId, from, to]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, loading, error, reload };
}
