import { fetchEventSource } from "@microsoft/fetch-event-source";
import { DEFAULT_WORKSPACE_TENANT_ID, DEV_BASIC_AUTHORIZATION } from "../api/apiFetch";
import { Allow, MalformedJSON, PartialJSON, parse } from "partial-json";
import { useCallback, useRef, useState } from "react";
import type { VerifyStreamChunkPayload } from "../types/analysis";

function parseVerifyStreamChunkPayload(raw: string): VerifyStreamChunkPayload | null {
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== "object" || parsed === null || !("kind" in parsed) || !("text" in parsed)) {
      return null;
    }
    const rec = parsed as Record<string, unknown>;
    if (typeof rec.kind !== "string" || typeof rec.text !== "string") {
      return null;
    }
    const kind = rec.kind;
    if (kind !== "delta" && kind !== "done" && kind !== "error") {
      return null;
    }
    const queryId = typeof rec.queryId === "string" ? rec.queryId : undefined;
    return { kind, text: rec.text, queryId };
  } catch {
    return null;
  }
}

function tryParseModelJson(json: string): unknown | null {
  if (json.length === 0) {
    return null;
  }
  try {
    return parse(json, Allow.ALL);
  } catch (caught: unknown) {
    if (caught instanceof PartialJSON || caught instanceof MalformedJSON) {
      return null;
    }
    return null;
  }
}

export interface UseJobStreamingResult {
  isStreaming: boolean;
  streamError: string | null;
  parsedByQueryId: Record<string, unknown>;
  connectJobStream: (jobId: string) => Promise<void>;
  disconnectJobStream: () => void;
}

export function useJobStreaming(
  onStreamSettled: () => void | Promise<void>,
): UseJobStreamingResult {
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamError, setStreamError] = useState<string | null>(null);
  const [parsedByQueryId, setParsedByQueryId] = useState<Record<string, unknown>>({});
  const buffersRef = useRef<Record<string, string>>({});
  const abortControllerRef = useRef<AbortController | null>(null);
  const settledRef = useRef(false);
  const onSettledRef = useRef(onStreamSettled);
  onSettledRef.current = onStreamSettled;

  const runSettledOnce = useCallback(async () => {
    if (settledRef.current) {
      return;
    }
    settledRef.current = true;
    setIsStreaming(false);
    try {
      await onSettledRef.current();
    } catch {
      return;
    }
  }, []);

  const disconnectJobStream = useCallback(() => {
    abortControllerRef.current?.abort();
    abortControllerRef.current = null;
    setIsStreaming(false);
  }, []);

  const connectJobStream = useCallback(
    async (jobId: string) => {
      const trimmed = jobId.trim();
      if (trimmed.length === 0) {
        return;
      }
      disconnectJobStream();
      settledRef.current = false;
      buffersRef.current = {};
      setParsedByQueryId({});
      setStreamError(null);
      setIsStreaming(true);
      const abortController = new AbortController();
      abortControllerRef.current = abortController;
      try {
        await fetchEventSource(`/api/v1/jobs/${trimmed}/stream`, {
          signal: abortController.signal,
          credentials: "include",
          headers: {
            "X-Tenant-ID": DEFAULT_WORKSPACE_TENANT_ID,
            Authorization: DEV_BASIC_AUTHORIZATION,
          },
          openWhenHidden: true,
          async onopen(response) {
            if (!response.ok) {
              const text = await response.text();
              throw new Error(text.length > 0 ? text : `HTTP ${response.status}`);
            }
          },
          onmessage(event) {
            if (event.event === "chunk") {
              const payload = parseVerifyStreamChunkPayload(event.data);
              if (payload === null) {
                return;
              }
              if (payload.kind === "error") {
                setStreamError(payload.text);
                void runSettledOnce();
                return;
              }
              const bufferKey = payload.queryId ?? "_";
              const buffers = buffersRef.current;
              if (payload.kind === "delta") {
                buffers[bufferKey] = (buffers[bufferKey] ?? "") + payload.text;
              } else {
                buffers[bufferKey] = payload.text;
              }
              const partial = tryParseModelJson(buffers[bufferKey] ?? "");
              setParsedByQueryId((previous) => {
                const next: Record<string, unknown> = { ...previous };
                if (partial !== null) {
                  next[bufferKey] = partial;
                }
                return next;
              });
              if (payload.kind === "done" && payload.queryId === undefined) {
                void runSettledOnce();
              }
              return;
            }
            if (event.event === "error") {
              try {
                const parsed: unknown = JSON.parse(event.data);
                const rec = parsed as Record<string, unknown>;
                const message = typeof rec.message === "string" ? rec.message : "stream error";
                setStreamError(message);
              } catch {
                setStreamError(event.data);
              }
              void runSettledOnce();
            }
          },
          onclose() {
            void runSettledOnce();
          },
          onerror(err) {
            setStreamError(err instanceof Error ? err.message : String(err));
            void runSettledOnce();
            throw err;
          },
        });
      } catch (caught: unknown) {
        if (caught instanceof Error && caught.name === "AbortError") {
          setIsStreaming(false);
          return;
        }
        if (!settledRef.current) {
          const message = caught instanceof Error ? caught.message : String(caught);
          setStreamError(message);
          await runSettledOnce();
        }
      } finally {
        abortControllerRef.current = null;
      }
    },
    [disconnectJobStream, runSettledOnce],
  );

  return {
    isStreaming,
    streamError,
    parsedByQueryId,
    connectJobStream,
    disconnectJobStream,
  };
}
