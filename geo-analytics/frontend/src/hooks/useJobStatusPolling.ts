import { apiFetch, parseJsonTextAsCamel } from "../api/apiFetch";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  normalizeJobStatusResponse,
  type JobStatusResponse,
} from "../types/analysis";

const POLL_INTERVAL_MS = 3000;

function parseJobUpdatedAtMs(updatedAt: string): number {
  const ms = Date.parse(updatedAt);
  return Number.isNaN(ms) ? 0 : ms;
}

function shouldApplyJobStatusUpdate(
  previous: JobStatusResponse | null,
  incoming: JobStatusResponse,
): boolean {
  if (previous === null) {
    return true;
  }
  return (
    parseJobUpdatedAtMs(incoming.updatedAt) >=
    parseJobUpdatedAtMs(previous.updatedAt)
  );
}

function mergeJobStatusPreservingSummary(
  previous: JobStatusResponse | null,
  incoming: JobStatusResponse,
): JobStatusResponse {
  const incomingHasSummary =
    (incoming.diagnosticMessage != null && incoming.diagnosticMessage.trim().length > 0) ||
    incoming.recommendedActions.length > 0 ||
    (incoming.jobMedianModifiedZ != null && !Number.isNaN(incoming.jobMedianModifiedZ));
  if (incomingHasSummary) {
    return incoming;
  }
  if (previous === null) {
    return incoming;
  }
  const previousHasSummary =
    (previous.diagnosticMessage != null && previous.diagnosticMessage.trim().length > 0) ||
    previous.recommendedActions.length > 0 ||
    (previous.jobMedianModifiedZ != null && !Number.isNaN(previous.jobMedianModifiedZ));
  if (!previousHasSummary) {
    return incoming;
  }
  return {
    ...incoming,
    diagnosticMessage: previous.diagnosticMessage,
    recommendedActions: previous.recommendedActions,
    jobMedianModifiedZ: previous.jobMedianModifiedZ,
  };
}

function computeNextJobStatus(
  previous: JobStatusResponse | null,
  normalized: JobStatusResponse,
): JobStatusResponse | null {
  if (!shouldApplyJobStatusUpdate(previous, normalized)) {
    return null;
  }
  return mergeJobStatusPreservingSummary(previous, normalized);
}

/** ジョブ完了後も PDF 生成中ならポーリングを継続する */
function shouldContinuePolling(status: JobStatusResponse | null): boolean {
  if (status === null) {
    return true;
  }
  const js = status.jobStatus ?? "";
  if (js === "FAILED") {
    return false;
  }
  if (js === "COMPLETED" || js === "SUCCEEDED") {
    return status.pdfStatus === "GENERATING";
  }
  return true;
}

export interface UseJobStatusPollingResult {
  jobStatus: JobStatusResponse | null;
  lastError: string | null;
  isLoading: boolean;
  refetchJobFromRest: () => Promise<void>;
}

export function useJobStatusPolling(jobId: string): UseJobStatusPollingResult {
  const [jobStatus, setJobStatus] = useState<JobStatusResponse | null>(null);
  const [lastError, setLastError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const previousJobIdRef = useRef<string>("");
  const jobStatusRef = useRef<JobStatusResponse | null>(null);

  const refetchJobFromRest = useCallback(async () => {
    const trimmedJobId = jobId.trim();
    if (trimmedJobId.length === 0) {
      return;
    }
    try {
      const res = await apiFetch(`/api/v1/jobs/${trimmedJobId}`);
      const responseText = await res.text();
      if (!res.ok) {
        return;
      }
      let parsed: unknown;
      try {
        parsed = parseJsonTextAsCamel(responseText) as unknown;
      } catch {
        return;
      }
      const normalized = normalizeJobStatusResponse(parsed);
      if (normalized === null) {
        return;
      }
      const next = computeNextJobStatus(jobStatusRef.current, normalized);
      if (next !== null) {
        jobStatusRef.current = next;
        setJobStatus(next);
      }
    } catch {
      return;
    }
  }, [jobId]);

  useEffect(() => {
    const trimmedJobId = jobId.trim();
    if (trimmedJobId.length === 0) {
      previousJobIdRef.current = "";
      jobStatusRef.current = null;
      setJobStatus(null);
      setLastError(null);
      setIsLoading(false);
      return undefined;
    }

    if (previousJobIdRef.current !== trimmedJobId) {
      jobStatusRef.current = null;
      setJobStatus(null);
      previousJobIdRef.current = trimmedJobId;
    }

    let cancelled = false;
    const abortController = new AbortController();
    let intervalId: ReturnType<typeof setInterval> | undefined;

    setLastError(null);
    setIsLoading(true);

    async function fetchOnce(): Promise<boolean> {
      try {
        const res = await apiFetch(`/api/v1/jobs/${trimmedJobId}`, {
          signal: abortController.signal,
        });
        const responseText = await res.text();

        if (res.status === 404) {
          console.error("job status fetch not found", res.status, responseText);
          jobStatusRef.current = null;
          setJobStatus(null);
          setLastError("ジョブが見つかりません");
          return false;
        }

        if (!res.ok) {
          console.error("job status fetch failed", res.status, responseText);
          setLastError(
            responseText.length > 0 ? responseText : `HTTP ${res.status}`,
          );
          return true;
        }

        let parsed: unknown;
        try {
          parsed = parseJsonTextAsCamel(responseText) as unknown;
        } catch {
          console.error(
            "job status JSON parse error",
            res.status,
            responseText,
          );
          setLastError("ジョブ状態の形式が不正です");
          return true;
        }

        const normalized = normalizeJobStatusResponse(parsed);
        if (normalized === null) {
          console.error(
            "job status validation failed",
            res.status,
            responseText,
          );
          setLastError("ジョブ状態の形式が不正です");
          return true;
        }

        const next = computeNextJobStatus(jobStatusRef.current, normalized);
        if (next !== null) {
          jobStatusRef.current = next;
          setJobStatus(next);
        }
        setLastError(null);
        return true;
      } catch (err: unknown) {
        if (err instanceof Error && err.name === "AbortError") {
          return true;
        }
        const message = err instanceof Error ? err.message : String(err);
        console.error("job status fetch error", message, err);
        setLastError(message);
        return true;
      }
    }

    void (async (): Promise<void> => {
      const continuePolling = await fetchOnce();
      if (cancelled) {
        return;
      }
      setIsLoading(false);
      if (!continuePolling) {
        return;
      }
      intervalId = setInterval(() => {
        if (cancelled) {
          return;
        }
        if (!shouldContinuePolling(jobStatusRef.current)) {
          if (intervalId !== undefined) {
            clearInterval(intervalId);
            intervalId = undefined;
          }
          return;
        }
        void fetchOnce();
      }, POLL_INTERVAL_MS);
    })();

    return () => {
      cancelled = true;
      abortController.abort();
      if (intervalId !== undefined) {
        clearInterval(intervalId);
      }
    };
  }, [jobId]);

  return { jobStatus, lastError, isLoading, refetchJobFromRest };
}
