import { useEffect, useRef } from "react";
import type { GrowthTrendRange } from "../lib/growthTrendRange";
import { useProjectAssetSnapshots, type AssetSnapshotChartPoint } from "./useProjectAssetSnapshots";

const RETRY_INTERVAL_MS = 3000;
const MAX_RETRIES = 4;

export function useGrowthTrend(projectId: string, range: GrowthTrendRange): AssetSnapshotChartPoint[] {
  const { data, loading, error, reload } = useProjectAssetSnapshots(projectId, range.from, range.to);
  const fetchedRef = useRef(false);
  const retriesRef = useRef(0);

  useEffect(() => {
    fetchedRef.current = false;
    retriesRef.current = 0;
  }, [projectId, range.from, range.to]);

  // Why: 推移の点は、解析が完了になったあと別の仮想スレッドで保存される（GeoAssetSnapshotPipeline）。
  //      完了直後に開いた画面ではその解析の点がまだ無いことがあるため、数回だけ取り直す。
  useEffect(() => {
    if (loading) {
      fetchedRef.current = true;
      return;
    }
    if (!fetchedRef.current || error !== null || retriesRef.current >= MAX_RETRIES) {
      return;
    }
    if (data.some((p) => p.snapshotDate === range.to)) {
      return;
    }
    const timer = window.setTimeout(() => {
      retriesRef.current += 1;
      void reload();
    }, RETRY_INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [loading, error, data, range.to, reload]);

  return data;
}
