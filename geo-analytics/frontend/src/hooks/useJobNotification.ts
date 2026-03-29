import { Client, IMessage } from "@stomp/stompjs";
import { useCallback, useEffect, useRef, useState } from "react";
import SockJS from "sockjs-client";
import { apiFetch } from "../api/apiFetch";
import {
  normalizeJobStatusResponse,
  type JobStatusResponse,
} from "../types/analysis";

export type JobNotificationConnectionState =
  | "idle"
  | "connecting"
  | "connected"
  | "disconnected"
  | "error";

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

function parseJobStatusMessage(body: string): JobStatusResponse | null {
  try {
    const parsed: unknown = JSON.parse(body);
    return normalizeJobStatusResponse(parsed);
  } catch {
    return null;
  }
}

function buildSockJsUrl(): string {
  if (typeof window === "undefined") {
    return "/ws";
  }
  return `${window.location.origin}/ws`;
}

export interface UseJobNotificationResult {
  jobStatus: JobStatusResponse | null;
  connectionState: JobNotificationConnectionState;
  lastError: string | null;
  isLoading: boolean;
  refetchJobFromRest: () => Promise<void>;
}

export function useJobNotification(jobId: string): UseJobNotificationResult {
  const [jobStatus, setJobStatus] = useState<JobStatusResponse | null>(null);
  const [connectionState, setConnectionState] =
    useState<JobNotificationConnectionState>("idle");
  const [lastError, setLastError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const previousJobIdRef = useRef<string>("");

  const handleMessage = useCallback((message: IMessage) => {
    const next = parseJobStatusMessage(message.body);
    if (next === null) {
      return;
    }
    setJobStatus((prev) => {
      if (!shouldApplyJobStatusUpdate(prev, next)) {
        return prev;
      }
      return mergeJobStatusPreservingSummary(prev, next);
    });
  }, []);

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
        parsed = JSON.parse(responseText) as unknown;
      } catch {
        return;
      }
      const normalized = normalizeJobStatusResponse(parsed);
      if (normalized === null) {
        return;
      }
      setJobStatus((prev) => {
        if (!shouldApplyJobStatusUpdate(prev, normalized)) {
          return prev;
        }
        return mergeJobStatusPreservingSummary(prev, normalized);
      });
    } catch {
      return;
    }
  }, [jobId]);

  useEffect(() => {
    const trimmedJobId = jobId.trim();
    if (trimmedJobId.length === 0) {
      previousJobIdRef.current = "";
      setJobStatus(null);
      setConnectionState("idle");
      setLastError(null);
      setIsLoading(false);
      return undefined;
    }

    if (previousJobIdRef.current !== trimmedJobId) {
      setJobStatus(null);
      previousJobIdRef.current = trimmedJobId;
    }

    let cancelled = false;
    let skipWebSocket = false;
    const abortController = new AbortController();
    let stompClient: Client | null = null;

    setConnectionState("connecting");
    setLastError(null);
    setIsLoading(true);

    void (async (): Promise<void> => {
      try {
        const res = await apiFetch(`/api/v1/jobs/${trimmedJobId}`, {
          signal: abortController.signal,
        });
        if (cancelled) {
          return;
        }

        const responseText = await res.text();

        if (res.status === 404) {
          console.error("job status fetch not found", res.status, responseText);
          setJobStatus(null);
          setLastError("ジョブが見つかりません");
          setConnectionState("idle");
          skipWebSocket = true;
          return;
        }

        if (!res.ok) {
          console.error("job status fetch failed", res.status, responseText);
          setLastError(
            responseText.length > 0 ? responseText : `HTTP ${res.status}`,
          );
        } else {
          let parsed: unknown;
          try {
            parsed = JSON.parse(responseText) as unknown;
          } catch {
            console.error(
              "job status JSON parse error",
              res.status,
              responseText,
            );
            setLastError("ジョブ状態の形式が不正です");
            parsed = null;
          }
          if (parsed !== null && !cancelled) {
            const normalized = normalizeJobStatusResponse(parsed);
            if (normalized === null) {
              console.error(
                "job status validation failed",
                res.status,
                responseText,
              );
              setLastError("ジョブ状態の形式が不正です");
            } else {
              setJobStatus((prev) => {
                if (!shouldApplyJobStatusUpdate(prev, normalized)) {
                  return prev;
                }
                return mergeJobStatusPreservingSummary(prev, normalized);
              });
            }
          }
        }
      } catch (err: unknown) {
        if (cancelled) {
          return;
        }
        if (err instanceof Error && err.name === "AbortError") {
          return;
        }
        const message = err instanceof Error ? err.message : String(err);
        console.error("job status fetch error", message, err);
        setLastError(message);
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }

      if (cancelled || skipWebSocket) {
        return;
      }

      stompClient = new Client({
        webSocketFactory: () => new SockJS(buildSockJsUrl()),
        reconnectDelay: 5000,
        onConnect: () => {
          if (cancelled) {
            return;
          }
          setConnectionState("connected");
          stompClient?.subscribe(`/topic/jobs/${trimmedJobId}`, handleMessage);
        },
        onStompError: (frame) => {
          setConnectionState("error");
          const headerMessage = frame.headers["message"];
          const message =
            typeof headerMessage === "string" && headerMessage.length > 0
              ? headerMessage
              : typeof frame.body === "string" && frame.body.length > 0
                ? frame.body
                : "STOMP error";
          console.error("STOMP error", message, frame.body);
          setLastError(message);
        },
        onWebSocketClose: () => {
          setConnectionState("disconnected");
        },
        onDisconnect: () => {
          setConnectionState("disconnected");
        },
      });

      if (cancelled) {
        stompClient?.deactivate();
        stompClient = null;
        return;
      }

      stompClient.activate();
    })();

    return () => {
      cancelled = true;
      abortController.abort();
      stompClient?.deactivate();
      stompClient = null;
    };
  }, [jobId, handleMessage]);

  return { jobStatus, connectionState, lastError, isLoading, refetchJobFromRest };
}
