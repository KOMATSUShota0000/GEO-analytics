import { fetchEventSource } from "@microsoft/fetch-event-source";
import { keysToCamelDeep } from "../api/apiFetch";
import { DEFAULT_WORKSPACE_TENANT_ID } from "../api/tenantConstants";
import { getAccessToken } from "../auth/authSession";
import { useCallback, useEffect, useRef, useState, type MutableRefObject } from "react";
import type {
  DebateOnboardingSseEventPayload,
  DebateOnboardingSseEventType,
  DebatePartialScoresPayload,
  DebateStreamPersona,
  DebateStreamPhase,
  OnboardingNarrationLogEntry,
  OnboardingScorePoint,
} from "../types/onboardingDebateStream";

const FLUSH_MS = 120;
const MAX_PRE_OPEN_ATTEMPTS = 14;
const INITIAL_BACKOFF_MS = 500;

export const ONBOARDING_STREAM_SETTLEMENT_NOTICE =
  "接続が切断されたか、エラーで処理が完了しませんでした。この場合でもご利用枠（クレジット等）の精算はサーバー側で正しく行われています。ご不明点があればサポートへお問い合わせください。";

type QueuedStreamChunk =
  | { kind: "narration"; entry: OnboardingNarrationLogEntry }
  | { kind: "score"; point: OnboardingScorePoint; radar: number[] };

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function buildOnboardingStreamUrl(projectId: string, sessionId: string): string {
  const pid = encodeURIComponent(projectId);
  const sid = encodeURIComponent(sessionId);
  return `/api/v1/projects/${pid}/onboarding/stream?session_id=${sid}`;
}

function parseDebatePayload(rawText: string): DebateOnboardingSseEventPayload | null {
  try {
    const parsed: unknown = keysToCamelDeep(JSON.parse(rawText));
    if (parsed === null || typeof parsed !== "object") {
      return null;
    }
    const o = parsed as Record<string, unknown>;
    const eventType = o.eventType;
    if (
      eventType !== "NARRATION"
      && eventType !== "SCORE_UPDATE"
      && eventType !== "PHASE_CHANGE"
      && eventType !== "DONE"
      && eventType !== "ERROR"
    ) {
      return null;
    }
    const persona = (o.persona ?? null) as DebateStreamPersona | null;
    const status = (o.status ?? null) as DebateStreamPhase;
    const message = typeof o.message === "string" ? o.message : null;
    let partialScores: DebatePartialScoresPayload | null = null;
    if (o.partialScores !== undefined && o.partialScores !== null && typeof o.partialScores === "object") {
      partialScores = o.partialScores as DebatePartialScoresPayload;
    }
    return {
      eventType,
      persona,
      status,
      message,
      partialScores,
      timestamp: typeof o.timestamp === "string" ? o.timestamp : null,
      sessionId: typeof o.sessionId === "string" ? o.sessionId : null,
    };
  } catch {
    return null;
  }
}

function streamHeaders(): Record<string, string> {
  const streamHeaders: Record<string, string> = {
    "X-Tenant-ID": DEFAULT_WORKSPACE_TENANT_ID,
  };
  const accessToken = getAccessToken();
  if (accessToken !== null && accessToken.length > 0) {
    streamHeaders.Authorization = `Bearer ${accessToken}`;
  }
  return streamHeaders;
}

function nextLogId(): string {
  return `${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 9)}`;
}

function applyMessageToPending(
  payload: DebateOnboardingSseEventPayload,
  runId: number,
  runIdRef: MutableRefObject<number>,
  queueRef: MutableRefObject<QueuedStreamChunk[]>,
  receivedDoneRef: MutableRefObject<boolean>,
  setStreamError: (v: string | null) => void,
  setSettlementNotice: (v: string | null) => void,
): void {
  if (runId !== runIdRef.current) {
    return;
  }
  const eventType = payload.eventType as DebateOnboardingSseEventType;
  if (eventType === "DONE") {
    receivedDoneRef.current = true;
    return;
  }
  if (eventType === "ERROR") {
    receivedDoneRef.current = true;
    const serverMsg = payload.message?.trim() ?? "";
    setStreamError(serverMsg.length > 0 ? serverMsg : "ストリームでエラーが返されました。");
    setSettlementNotice(ONBOARDING_STREAM_SETTLEMENT_NOTICE);
    return;
  }
  if (eventType === "NARRATION" || eventType === "PHASE_CHANGE") {
    const text = payload.message?.trim() ?? "";
    if (text.length === 0) {
      return;
    }
    const entry: OnboardingNarrationLogEntry = {
      id: nextLogId(),
      eventType,
      persona: payload.persona ?? null,
      phase: payload.status ?? null,
      message: text,
      atMs: Date.now(),
    };
    queueRef.current.push({ kind: "narration", entry });
    return;
  }
  if (eventType === "SCORE_UPDATE") {
    const ps = payload.partialScores;
    const round = typeof ps?.round === "number" ? ps.round : null;
    const geoIg = typeof ps?.geoIg === "number" && Number.isFinite(ps.geoIg) ? ps.geoIg : null;
    if (round === null || geoIg === null) {
      return;
    }
    const w1 =
      typeof ps?.wasserstein1 === "number" && Number.isFinite(ps.wasserstein1) ? ps.wasserstein1 : null;
    const pSite = Array.isArray(ps?.pSite) ? ps.pSite.filter((n) => typeof n === "number" && Number.isFinite(n)) : [];
    const radar = pSite.length >= 4 ? pSite.slice(0, 4) : pSite;
    queueRef.current.push({
      kind: "score",
      point: { round, geoIg, wasserstein1: w1 },
      radar,
    });
  }
}

export interface UseOnboardingStreamResult {
  narrationLog: OnboardingNarrationLogEntry[];
  scoreSeries: OnboardingScorePoint[];
  latestRadar: number[] | null;
  streamError: string | null;
  settlementNotice: string | null;
  streamRunning: boolean;
  startStream: (projectId: string, sessionId: string) => Promise<void>;
  stopStream: () => void;
  resetStreamUiState: () => void;
  dismissStreamAlerts: () => void;
}

export function useOnboardingStream(): UseOnboardingStreamResult {
  const [narrationLog, setNarrationLog] = useState<OnboardingNarrationLogEntry[]>([]);
  const [scoreSeries, setScoreSeries] = useState<OnboardingScorePoint[]>([]);
  const [latestRadar, setLatestRadar] = useState<number[] | null>(null);
  const [streamError, setStreamError] = useState<string | null>(null);
  const [settlementNotice, setSettlementNotice] = useState<string | null>(null);
  const [streamRunning, setStreamRunning] = useState(false);

  const abortRef = useRef<AbortController | null>(null);
  const runIdRef = useRef(0);
  const receivedDoneRef = useRef(false);
  const queueRef = useRef<QueuedStreamChunk[]>([]);
  const flushIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const flushQueueToReact = useCallback(() => {
    const batch = queueRef.current;
    if (batch.length === 0) {
      return;
    }
    queueRef.current = [];
    const narrations: OnboardingNarrationLogEntry[] = [];
    let lastScore: OnboardingScorePoint | null = null;
    let lastRadar: number[] | null = null;
    for (const item of batch) {
      if (item.kind === "narration") {
        narrations.push(item.entry);
      } else {
        lastScore = item.point;
        lastRadar = item.radar.length > 0 ? item.radar : lastRadar;
      }
    }
    if (narrations.length > 0) {
      setNarrationLog((prev) => [...prev, ...narrations]);
    }
    if (lastScore !== null) {
      setScoreSeries((prev) => {
        const withoutSameRound = prev.filter((p) => p.round !== lastScore.round);
        return [...withoutSameRound, lastScore!].sort((a, b) => a.round - b.round);
      });
    }
    if (lastRadar !== null) {
      setLatestRadar(lastRadar);
    }
  }, []);

  const stopFlushTimer = useCallback(() => {
    if (flushIntervalRef.current !== null) {
      clearInterval(flushIntervalRef.current);
      flushIntervalRef.current = null;
    }
  }, []);

  const startFlushTimer = useCallback(() => {
    if (flushIntervalRef.current !== null) {
      return;
    }
    flushIntervalRef.current = setInterval(() => {
      flushQueueToReact();
    }, FLUSH_MS);
  }, [flushQueueToReact]);

  const stopStream = useCallback(() => {
    runIdRef.current += 1;
    stopFlushTimer();
    flushQueueToReact();
    abortRef.current?.abort();
    abortRef.current = null;
    setStreamRunning(false);
  }, [flushQueueToReact, stopFlushTimer]);

  const resetStreamUiState = useCallback(() => {
    queueRef.current = [];
    setNarrationLog([]);
    setScoreSeries([]);
    setLatestRadar(null);
    setStreamError(null);
    setSettlementNotice(null);
    receivedDoneRef.current = false;
  }, []);

  const startStream = useCallback(
    async (projectId: string, sessionId: string) => {
      stopStream();
      resetStreamUiState();
      receivedDoneRef.current = false;
      setStreamRunning(true);
      const runId = runIdRef.current;
      const ac = new AbortController();
      abortRef.current = ac;
      startFlushTimer();

      let preOpenAttempts = 0;
      let backoffMs = INITIAL_BACKOFF_MS;
      let openResolved = false;

      await new Promise<void>((resolve, reject) => {
        const resolveOpenOnce = (): void => {
          if (openResolved) {
            return;
          }
          openResolved = true;
          resolve();
        };

        const failOpen = (err: unknown): void => {
          if (openResolved || ac.signal.aborted) {
            return;
          }
          reject(err instanceof Error ? err : new Error(String(err)));
        };

        async function attemptConnection(): Promise<void> {
          while (!ac.signal.aborted && preOpenAttempts < MAX_PRE_OPEN_ATTEMPTS) {
            preOpenAttempts += 1;
            let openedThisSession = false;
            try {
              await fetchEventSource(buildOnboardingStreamUrl(projectId, sessionId), {
                signal: ac.signal,
                credentials: "include",
                openWhenHidden: true,
                headers: streamHeaders(),
                async onopen(response) {
                  if (!response.ok) {
                    const text = await response.text();
                    throw new Error(text.length > 0 ? text : `HTTP ${response.status}`);
                  }
                  openedThisSession = true;
                  resolveOpenOnce();
                },
                onmessage(ev) {
                  if (runId !== runIdRef.current) {
                    return;
                  }
                  if (ev.event !== "debate" || typeof ev.data !== "string") {
                    return;
                  }
                  const payload = parseDebatePayload(ev.data);
                  if (payload === null) {
                    return;
                  }
                  applyMessageToPending(
                    payload,
                    runId,
                    runIdRef,
                    queueRef,
                    receivedDoneRef,
                    setStreamError,
                    setSettlementNotice,
                  );
                },
              });
              if (ac.signal.aborted) {
                return;
              }
              if (!receivedDoneRef.current && openedThisSession) {
                setSettlementNotice(ONBOARDING_STREAM_SETTLEMENT_NOTICE);
              }
              return;
            } catch (caught: unknown) {
              if (ac.signal.aborted) {
                return;
              }
              if (!openedThisSession) {
                await sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 32_000);
                continue;
              }
              if (!receivedDoneRef.current) {
                setSettlementNotice(ONBOARDING_STREAM_SETTLEMENT_NOTICE);
              }
              setStreamError(caught instanceof Error ? caught.message : String(caught));
              return;
            }
          }
          if (!ac.signal.aborted && !openResolved) {
            failOpen(new Error("リアルタイム接続を確立できませんでした。ネットワークを確認のうえ、しばらくしてからお試しください。"));
          }
        }

        void attemptConnection().catch(failOpen);
      });
    },
    [resetStreamUiState, startFlushTimer, stopStream],
  );

  const dismissStreamAlerts = useCallback(() => {
    setStreamError(null);
    setSettlementNotice(null);
  }, []);

  useEffect(() => {
    return () => {
      stopStream();
    };
  }, [stopStream]);

  return {
    narrationLog,
    scoreSeries,
    latestRadar,
    streamError,
    settlementNotice,
    streamRunning,
    startStream,
    stopStream,
    resetStreamUiState,
    dismissStreamAlerts,
  };
}
