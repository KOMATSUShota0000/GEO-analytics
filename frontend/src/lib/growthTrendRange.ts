import type { ResultDetail } from "../types/analysis";

const RANGE_DAYS = 90;

export type GrowthTrendRange = { from: string; to: string };

function tokyoIsoDate(d: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Tokyo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
}

// Why: 古い解析の画面・PDF に、そのあとの解析の点を混ぜないよう、期間を「その解析の日まで」で切る（#224）。
//      推移の点は日本時間の日付で記録される（GeoAssetSnapshotService）。auditDate はサーバーの既定のタイムゾーンで
//      記録されてずれることがあるため、時差を含む createdAt を日本時間に直して使う。
export function growthTrendRangeForJob(results: ResultDetail[], now: Date = new Date()): GrowthTrendRange {
  const times = results.map((r) => Date.parse(r.createdAt)).filter((ms) => Number.isFinite(ms));
  const to = tokyoIsoDate(times.length > 0 ? new Date(Math.max(...times)) : now);
  const from = new Date(`${to}T00:00:00Z`);
  from.setUTCDate(from.getUTCDate() - RANGE_DAYS);
  return { from: from.toISOString().slice(0, 10), to };
}
