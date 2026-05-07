const STORAGE_KEY = "geo.bannerJobHint.v1";

type Entry = { jobId: string; sortMs: number };

function readEntries(): Record<string, Entry> {
  if (typeof window === "undefined") {
    return {};
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (raw === null || raw.length === 0) {
      return {};
    }
    const parsed = JSON.parse(raw) as unknown;
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {};
    }
    const o = parsed as Record<string, unknown>;
    const out: Record<string, Entry> = {};
    for (const k of Object.keys(o)) {
      const row = o[k];
      if (row === null || typeof row !== "object" || Array.isArray(row)) {
        continue;
      }
      const rec = row as Record<string, unknown>;
      const jobId = typeof rec.jobId === "string" ? rec.jobId.trim() : "";
      const sortMsRaw = rec.sortMs;
      const sortMs =
        typeof sortMsRaw === "number" && !Number.isNaN(sortMsRaw) ? sortMsRaw : Number.NaN;
      if (jobId.length === 0 || Number.isNaN(sortMs)) {
        continue;
      }
      out[k.trim()] = { jobId, sortMs };
    }
    return out;
  } catch {
    return {};
  }
}

function writeEntries(next: Record<string, Entry>): void {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
  }
}

export function mergeBannerJobHint(projectId: string, jobId: string, sortMs: number): void {
  const pid = projectId.trim();
  const jid = jobId.trim();
  if (pid.length === 0 || jid.length === 0) {
    return;
  }
  const ms = Number.isFinite(sortMs) ? sortMs : Date.now();
  const map = readEntries();
  const prev = map[pid];
  if (prev !== undefined && prev.sortMs > ms) {
    return;
  }
  map[pid] = { jobId: jid, sortMs: ms };
  writeEntries(map);
}

export function getBannerJobHintJobId(projectId: string): string | null {
  const pid = projectId.trim();
  if (pid.length === 0) {
    return null;
  }
  const row = readEntries()[pid];
  return row !== undefined ? row.jobId : null;
}
