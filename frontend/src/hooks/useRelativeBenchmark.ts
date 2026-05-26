import { useCallback, useEffect, useState } from "react";
import { apiFetch, responseJsonAsCamel } from "../api/apiFetch";

export type BenchmarkRow = {
  label: string;
  selfLabel: string;
  competitorLabel: string;
  gap: boolean;
};

export type UseRelativeBenchmarkResult = {
  rows: BenchmarkRow[];
  locked: boolean;
  available: boolean;
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
};

export function useRelativeBenchmark(projectId: string): UseRelativeBenchmarkResult {
  const [rows, setRows] = useState<BenchmarkRow[]>([]);
  const [locked, setLocked] = useState(false);
  const [available, setAvailable] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const pid = projectId.trim();
    if (pid.length === 0) {
      setRows([]);
      setLocked(false);
      setAvailable(false);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const url = `/api/v1/projects/${encodeURIComponent(pid)}/relative-benchmark`;
      const response = await apiFetch(url);
      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
      }
      const body = (await responseJsonAsCamel(response)) as {
        locked?: unknown;
        available?: unknown;
        rows?: unknown;
      };
      const rawRows = Array.isArray(body.rows) ? body.rows : [];
      const next: BenchmarkRow[] = rawRows.map((row: unknown) => {
        const o = row as Record<string, unknown>;
        return {
          label: String(o.label ?? ""),
          selfLabel: String(o.selfLabel ?? ""),
          competitorLabel: String(o.competitorLabel ?? ""),
          gap: Boolean(o.gap),
        };
      });
      setLocked(Boolean(body.locked));
      setAvailable(Boolean(body.available));
      setRows(next);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : String(err);
      setError(message);
      setRows([]);
      setLocked(false);
      setAvailable(false);
    } finally {
      setLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { rows, locked, available, loading, error, reload };
}
