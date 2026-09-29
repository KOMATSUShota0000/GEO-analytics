import { useEffect, useRef, useState } from "react";
import type { GrowthTrendRange } from "../lib/growthTrendRange";
import { useProjectAssetSnapshots, type AssetSnapshotChartPoint } from "./useProjectAssetSnapshots";

const RETRY_INTERVAL_MS = 3000;
const MAX_RETRIES = 4;

export type GrowthTrend = {
  data: AssetSnapshotChartPoint[];
  /** 1回目の取得が終わった（失敗も含む）。取り直しの完了は待たない。 */
  settled: boolean;
};

// Why: 印刷用の画面（#225）では取り直しを止める。古い解析で期間の終わりの日の点がもともと無いと、
//      取り直しを待つあいだ印刷が十数秒遅れるため。完了直後に印刷すると最新の点が載らないことはあるが、受け入れる。
export function useGrowthTrend(
  projectId: string,
  range: GrowthTrendRange,
  retryUntilLatest = true,
): GrowthTrend {
  const { data, loading, error, reload } = useProjectAssetSnapshots(projectId, range.from, range.to);
  const fetchedRef = useRef(false);
  const retriesRef = useRef(0);
  const key = `${projectId}|${range.from}|${range.to}`;
  const [settledKey, setSettledKey] = useState<string | null>(null);

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
    if (!fetchedRef.current) {
      return;
    }
    setSettledKey(key);
    if (!retryUntilLatest || error !== null || retriesRef.current >= MAX_RETRIES) {
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
  }, [loading, error, data, range.to, reload, key, retryUntilLatest]);

  return { data, settled: projectId.trim().length === 0 || settledKey === key };
}
