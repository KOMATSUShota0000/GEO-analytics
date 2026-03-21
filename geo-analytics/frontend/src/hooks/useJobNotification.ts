import { Client, IMessage } from "@stomp/stompjs";
import { useCallback, useEffect, useRef, useState } from "react";
import SockJS from "sockjs-client";
import type { JobStatusResponse } from "../types/analysis";

export type JobNotificationConnectionState =
  | "idle"
  | "connecting"
  | "connected"
  | "disconnected"
  | "error";

function isJobStatusResponse(value: unknown): value is JobStatusResponse {
  if (value === null || typeof value !== "object") {
    return false;
  }
  const record = value as Record<string, unknown>;
  return (
    typeof record.jobId === "string" &&
    typeof record.jobStatus === "string" &&
    typeof record.brandName === "string" &&
    (record.errorMessage === null || typeof record.errorMessage === "string") &&
    typeof record.createdAt === "string" &&
    typeof record.updatedAt === "string"
  );
}

function parseJobStatusMessage(body: string): JobStatusResponse | null {
  try {
    const parsed: unknown = JSON.parse(body);
    if (!isJobStatusResponse(parsed)) {
      return null;
    }
    return parsed;
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
}

export function useJobNotification(jobId: string): UseJobNotificationResult {
  const [jobStatus, setJobStatus] = useState<JobStatusResponse | null>(null);
  const [connectionState, setConnectionState] =
    useState<JobNotificationConnectionState>("idle");
  const [lastError, setLastError] = useState<string | null>(null);
  const previousJobIdRef = useRef<string>("");

  const handleMessage = useCallback((message: IMessage) => {
    const next = parseJobStatusMessage(message.body);
    if (next !== null) {
      setJobStatus(next);
    }
  }, []);

  useEffect(() => {
    const trimmedJobId = jobId.trim();
    if (trimmedJobId.length === 0) {
      previousJobIdRef.current = "";
      setJobStatus(null);
      setConnectionState("idle");
      setLastError(null);
      return undefined;
    }

    if (previousJobIdRef.current !== trimmedJobId) {
      setJobStatus(null);
      previousJobIdRef.current = trimmedJobId;
    }
    setConnectionState("connecting");
    setLastError(null);

    const client = new Client({
      webSocketFactory: () => new SockJS(buildSockJsUrl()),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnectionState("connected");
        client.subscribe(`/topic/jobs/${trimmedJobId}`, handleMessage);
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
        setLastError(message);
      },
      onWebSocketClose: () => {
        setConnectionState("disconnected");
      },
      onDisconnect: () => {
        setConnectionState("disconnected");
      },
    });

    client.activate();

    return () => {
      client.deactivate();
      setConnectionState("disconnected");
    };
  }, [jobId, handleMessage]);

  return { jobStatus, connectionState, lastError };
}
