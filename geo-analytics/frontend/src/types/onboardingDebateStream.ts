export type DebateOnboardingSseEventType =
  | "NARRATION"
  | "SCORE_UPDATE"
  | "PHASE_CHANGE"
  | "DONE"
  | "ERROR";

export type DebateStreamPersona =
  | "ANALYST"
  | "INNOVATOR"
  | "SKEPTIC"
  | "DIRECTOR"
  | "SYSTEM";

export type DebateStreamPhase =
  | "GATHERING"
  | "ANALYZING"
  | "DEBATING"
  | "CONVERGING"
  | null;

export type DebatePartialScoresPayload = {
  round?: number | null;
  pSite?: number[];
  agentMass?: number[];
  sDensity?: number | null;
  qIntent?: number | null;
  currConfidences?: number[];
  currCentroid?: number[];
  geoIg?: number | null;
  wasserstein1?: number | null;
};

export type DebateOnboardingSseEventPayload = {
  eventType: DebateOnboardingSseEventType;
  persona?: DebateStreamPersona | null;
  status?: DebateStreamPhase;
  message?: string | null;
  partialScores?: DebatePartialScoresPayload | null;
  timestamp?: string | null;
  sessionId?: string | null;
};

export type OnboardingNarrationLogEntry = {
  id: string;
  eventType: DebateOnboardingSseEventType;
  persona: DebateStreamPersona | null;
  phase: DebateStreamPhase;
  message: string;
  atMs: number;
};

export type OnboardingScorePoint = {
  round: number;
  geoIg: number;
  wasserstein1: number | null;
};
