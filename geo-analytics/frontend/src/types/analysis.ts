export function formatAuditDate(isoDate: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  if (!m) {
    return isoDate;
  }
  const y = Number(m[1]);
  const mo = Number(m[2]);
  const d = Number(m[3]);
  return new Date(y, mo - 1, d).toLocaleDateString("ja-JP", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

type JsonDict = Record<string, unknown>;

function pickNum(r: JsonDict, camel: string, snake: string): number | undefined {
  const v = r[camel] !== undefined ? r[camel] : r[snake];
  return typeof v === "number" && !Number.isNaN(v) ? v : undefined;
}

function pickBool(r: JsonDict, camel: string, snake: string): boolean | undefined {
  const v = r[camel] !== undefined ? r[camel] : r[snake];
  return typeof v === "boolean" ? v : undefined;
}

export interface JobStatusResponse {
  jobId: string;
  projectId: string | null;
  jobStatus: string;
  brandName: string;
  errorMessage: string | null;
  pdfStatus: string | null;
  pdfFilePath: string | null;
  createdAt: string;
  updatedAt: string;
  diagnosticMessage: string | null;
  recommendedActions: string[];
  jobMedianModifiedZ: number | null;
}

export function normalizeJobStatusResponse(value: unknown): JobStatusResponse | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const r = value as JsonDict;
  const jobId = typeof r.jobId === "string" ? r.jobId : undefined;
  if (jobId === undefined) {
    return null;
  }
  const projectIdRaw = r.projectId;
  if (
    projectIdRaw !== undefined &&
    projectIdRaw !== null &&
    typeof projectIdRaw !== "string"
  ) {
    return null;
  }
  const jobStatus = typeof r.jobStatus === "string" ? r.jobStatus : undefined;
  const brandName = typeof r.brandName === "string" ? r.brandName : undefined;
  if (jobStatus === undefined || brandName === undefined) {
    return null;
  }
  const errRaw = r.errorMessage;
  if (errRaw !== undefined && errRaw !== null && typeof errRaw !== "string") {
    return null;
  }
  const psRaw = r.pdfStatus;
  if (psRaw !== undefined && psRaw !== null && typeof psRaw !== "string") {
    return null;
  }
  const pfpRaw = r.pdfFilePath;
  if (pfpRaw !== undefined && pfpRaw !== null && typeof pfpRaw !== "string") {
    return null;
  }
  const createdAt = typeof r.createdAt === "string" ? r.createdAt : undefined;
  const updatedAt = typeof r.updatedAt === "string" ? r.updatedAt : undefined;
  if (createdAt === undefined || updatedAt === undefined) {
    return null;
  }
  const dmRaw = r.diagnosticMessage;
  const dm =
    dmRaw === undefined || dmRaw === null
      ? null
      : typeof dmRaw === "string"
        ? dmRaw
        : null;
  const raRaw = r.recommendedActions;
  const recommendedActions =
    Array.isArray(raRaw) && raRaw.every((x): x is string => typeof x === "string")
      ? raRaw
      : [];
  const jmzRaw = r.jobMedianModifiedZ;
  const jmz =
    jmzRaw === undefined || jmzRaw === null
      ? null
      : typeof jmzRaw === "number" && !Number.isNaN(jmzRaw)
        ? jmzRaw
        : null;
  return {
    jobId,
    projectId:
      projectIdRaw === undefined || projectIdRaw === null || typeof projectIdRaw !== "string"
        ? null
        : projectIdRaw,
    jobStatus,
    brandName,
    errorMessage: errRaw === undefined || errRaw === null ? null : errRaw,
    pdfStatus: psRaw === undefined || psRaw === null ? null : psRaw,
    pdfFilePath: pfpRaw === undefined || pfpRaw === null ? null : pfpRaw,
    createdAt,
    updatedAt,
    diagnosticMessage: dm,
    recommendedActions,
    jobMedianModifiedZ: jmz,
  };
}

export interface TrendData {
  date: string;
  somScore: number;
  overallScore: number;
}
export interface CompetitorShare {
  name: string;
  value: number;
}
export type SubscriptionPlanApi = "STANDARD" | "PRO";
export interface AnalyticsSummaryNormalized {
  trend: TrendData[];
  share: CompetitorShare[];
  subscriptionPlan: SubscriptionPlanApi;
}
export function normalizeAnalyticsSummary(raw: unknown): AnalyticsSummaryNormalized | null {
  if (raw === null || typeof raw !== "object") {
    return null;
  }
  const r = raw as Record<string, unknown>;
  const trendRaw = r.trendData;
  const shareRaw = r.competitorShares;
  const planRaw = r.subscriptionPlan;
  if (!Array.isArray(trendRaw) || !Array.isArray(shareRaw) || typeof planRaw !== "string") {
    return null;
  }
  if (planRaw !== "STANDARD" && planRaw !== "PRO") {
    return null;
  }
  const trend: TrendData[] = [];
  for (const item of trendRaw) {
    if (item === null || typeof item !== "object") {
      return null;
    }
    const o = item as Record<string, unknown>;
    const ad = o.auditDate;
    const som = o.averageSomScore;
    const ov = o.averageOverallScore;
    if (typeof ad !== "string" || typeof som !== "number" || Number.isNaN(som)) {
      return null;
    }
    const overall =
      typeof ov === "number" && !Number.isNaN(ov) ? ov : som;
    trend.push({ date: ad.slice(0, 10), somScore: som, overallScore: overall });
  }
  const share: CompetitorShare[] = [];
  for (const item of shareRaw) {
    if (item === null || typeof item !== "object") {
      return null;
    }
    const o = item as Record<string, unknown>;
    const nm = o.name;
    const sh = o.share;
    if (typeof nm !== "string" || typeof sh !== "number" || Number.isNaN(sh)) {
      return null;
    }
    share.push({ name: nm, value: sh });
  }
  return { trend, share, subscriptionPlan: planRaw };
}
export interface JobProjectInfo {
  projectId: string;
  projectName: string;
  targetUrl: string;
  competitorUrls: string[];
  brandColor: string;
  logoUrl: string | null;
  industryType?: string;
}
export function parseJobProjectInfo(raw: unknown): JobProjectInfo | null {
  if (raw === null || typeof raw !== "object") {
    return null;
  }
  const p = raw as JsonDict;
  const projectId = typeof p.projectId === "string" ? p.projectId : undefined;
  const projectName = typeof p.projectName === "string" ? p.projectName : undefined;
  const targetUrl = typeof p.targetUrl === "string" ? p.targetUrl : undefined;
  if (projectId === undefined || projectName === undefined || targetUrl === undefined) {
    return null;
  }
  const compRaw = p.competitorUrls;
  const comp = Array.isArray(compRaw)
    ? compRaw.filter((x): x is string => typeof x === "string")
    : [];
  const bcRaw = p.brandColor;
  const bc =
    typeof bcRaw === "string" && bcRaw.length > 0
      ? bcRaw
      : "#4F46E5";
  const luRaw = p.logoUrl;
  const lu =
    luRaw === null || luRaw === undefined
      ? null
      : typeof luRaw === "string" && luRaw.length > 0
        ? luRaw
        : null;
  const indRaw = p.industryType ?? p.industry_type;
  const industryType = typeof indRaw === "string" && indRaw.length > 0 ? indRaw : undefined;
  return {
    projectId,
    projectName,
    targetUrl,
    competitorUrls: comp,
    brandColor: bc,
    logoUrl: lu,
    industryType,
  };
}
export interface LiveResultMetrics {
  somScore: number | null;
  overallScore: number | null;
  brandMentioned: boolean | null;
  mentionRank: number | null;
}
export function liveMetricsFromParsed(parsed: unknown): LiveResultMetrics {
  const empty: LiveResultMetrics = {
    somScore: null,
    overallScore: null,
    brandMentioned: null,
    mentionRank: null,
  };
  if (parsed === null || typeof parsed !== "object") {
    return empty;
  }
  const r = parsed as JsonDict;
  const overallScore =
    pickNum(r, "overallScore", "overall_score") ?? null;
  const confidenceScore =
    pickNum(r, "confidenceScore", "confidence_score") ?? null;
  const somScore = confidenceScore ?? overallScore;
  let brandMentioned: boolean | null = null;
  const bm = pickBool(r, "brandMentioned", "brand_mentioned");
  if (bm !== undefined) {
    brandMentioned = bm;
  }
  let mentionRank: number | null = null;
  const mr = pickNum(r, "mentionRank", "mention_rank");
  if (mr !== undefined && !Number.isNaN(mr)) {
    mentionRank = mr;
  }
  return { somScore, overallScore, brandMentioned, mentionRank };
}
function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}
function isoDateFromLocal(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}
export function aggregateTrendFromResultDetails(rows: ResultDetail[]): TrendData[] {
  if (rows.length === 0) {
    return [];
  }
  const byDate = new Map<string, { som: number; overall: number; n: number }>();
  for (const r of rows) {
    const key = r.auditDate;
    const ov = r.overallScore ?? r.somScore;
    const cur = byDate.get(key) ?? { som: 0, overall: 0, n: 0 };
    cur.som += r.somScore;
    cur.overall += ov;
    cur.n += 1;
    byDate.set(key, cur);
  }
  return [...byDate.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, v]) => ({
      date,
      somScore: Math.round((v.som / v.n) * 10) / 10,
      overallScore: Math.round((v.overall / v.n) * 10) / 10,
    }));
}
export function averageLiveScoresFromParsed(
  parsedByQueryId: Record<string, unknown>,
): { somScore: number; overallScore: number } | null {
  const ids = Object.keys(parsedByQueryId);
  if (ids.length === 0) {
    return null;
  }
  let somSum = 0;
  let ovSum = 0;
  let somN = 0;
  let ovN = 0;
  for (const id of ids) {
    const m = liveMetricsFromParsed(parsedByQueryId[id]);
    if (m.somScore !== null) {
      somSum += m.somScore;
      somN += 1;
    }
    if (m.overallScore !== null) {
      ovSum += m.overallScore;
      ovN += 1;
    }
  }
  if (somN === 0 && ovN === 0) {
    return null;
  }
  const som = somN > 0 ? somSum / somN : ovN > 0 ? ovSum / ovN : 0;
  const overall = ovN > 0 ? ovSum / ovN : som;
  return {
    somScore: Math.round(som * 10) / 10,
    overallScore: Math.round(overall * 10) / 10,
  };
}
export function competitorLabelsFromProject(project: JobProjectInfo | null): [string, string] {
  const urls = project?.competitorUrls ?? [];
  const a =
    urls[0] !== undefined
      ? (() => {
          try {
            const h = new URL(urls[0]).hostname.replace(/^www\./, "");
            return h.length > 0 ? h : "競合A";
          } catch {
            return "競合A";
          }
        })()
      : "競合A";
  const b =
    urls[1] !== undefined
      ? (() => {
          try {
            const h = new URL(urls[1]).hostname.replace(/^www\./, "");
            return h.length > 0 ? h : "競合B";
          } catch {
            return "競合B";
          }
        })()
      : "競合B";
  return [a, b];
}
export function buildCompetitorShareData(
  brandName: string,
  competitorNames: [string, string],
  anchorSomScore: number | null,
): CompetitorShare[] {
  if (anchorSomScore === null || Number.isNaN(anchorSomScore)) {
    return [];
  }
  const self = Math.max(0, Math.min(100, Math.round(anchorSomScore)));
  const rest = 100 - self;
  const v1 = Math.round(rest * 0.52);
  const v2 = rest - v1;
  return [
    { name: brandName, value: self },
    { name: competitorNames[0], value: v1 },
    { name: competitorNames[1], value: v2 },
  ];
}
export function resolveChartTrendData(
  resultRows: ResultDetail[],
  parsedByQueryId: Record<string, unknown>,
  isStreaming: boolean,
): TrendData[] {
  const agg = aggregateTrendFromResultDetails(resultRows);
  if (agg.length > 0) {
    return agg;
  }
  if (isStreaming) {
    const live = averageLiveScoresFromParsed(parsedByQueryId);
    if (live !== null) {
      const lastDate = isoDateFromLocal(new Date());
      return [
        {
          date: lastDate,
          somScore: live.somScore,
          overallScore: live.overallScore,
        },
      ];
    }
  }
  return [];
}
export function resolveAverageSomScore(
  resultRows: ResultDetail[],
  parsedByQueryId: Record<string, unknown>,
  isStreaming: boolean,
): number | null {
  if (resultRows.length > 0) {
    return resultRows.reduce((s, r) => s + r.somScore, 0) / resultRows.length;
  }
  if (isStreaming) {
    const live = averageLiveScoresFromParsed(parsedByQueryId);
    return live !== null ? live.somScore : null;
  }
  return null;
}

export function resolveChartShareData(
  brandName: string,
  competitorNames: [string, string],
  resultRows: ResultDetail[],
  parsedByQueryId: Record<string, unknown>,
  isStreaming: boolean,
): CompetitorShare[] {
  let anchor: number | null = null;
  if (resultRows.length > 0) {
    const sum = resultRows.reduce((s, r) => s + r.somScore, 0);
    anchor = sum / resultRows.length;
  } else {
    const live = averageLiveScoresFromParsed(parsedByQueryId);
    if (live !== null && isStreaming) {
      anchor = live.somScore;
    }
  }
  return buildCompetitorShareData(brandName, competitorNames, anchor);
}

export interface VerifyStreamChunkPayload {
  kind: "delta" | "done" | "error";
  text: string;
  queryId?: string;
}

export interface SseStreamErrorBody {
  message: string;
}

export interface ResultDetail {
  resultId: string;
  query: string;
  somScore: number;
  gbvsNormalizedScore?: number;
  brandMentioned: boolean;
  mentionRank: number | null;
  overallScore: number | null;
  tokenCount?: number;
  aiCitationPosition?: number | null;
  sentimentIntensity?: number;
  resolvedEntityLabel?: string | null;
  visibilityStage?: number | null;
  visibilityStageBand?: string | null;
  visibilityStageNarrative?: string | null;
  calculationVersion?: string | null;
  negativeAlert?: boolean;
  modifiedZScore?: number | null;
  diagnosticMessage?: string | null;
  recommendedActions?: string[];
  significantDeviation?: boolean | null;
  rawResponse: string;
  auditDate: string;
  createdAt: string;
}

export interface ScoreBreakdown {
  aiAuditTotal: number;
  meoTotal: number;
  machineReadabilityTotal: number;
  finalScore: number;
}

export type RemediationTaskCategory = "SPIKE" | "SLAB";
export type RemediationTaskPriority = "S" | "A" | "B";

export interface RemediationTask {
  id: string;
  category: RemediationTaskCategory;
  priority: RemediationTaskPriority;
  title: string;
  content: string;
  impactScore: number;
  level: number;
  requiredScoreThreshold: number;
  isMasked: boolean;
  targetSection?: string;
}

export type RemediationTaskTone = "PROFESSIONAL" | "FRIENDLY" | "AGGRESSIVE";

export type EmotionalAlertLevel = "DANGER" | "WARNING" | "INFO";

export interface EmotionalAlertPayload {
  level: EmotionalAlertLevel;
  message: string;
  usedFallback: boolean;
}

export function parseEmotionalAlertPayload(raw: unknown): EmotionalAlertPayload | null {
  if (raw === null || raw === undefined || typeof raw !== "object" || Array.isArray(raw)) {
    return null;
  }
  const r = raw as JsonDict;
  const levelRaw = r.level;
  if (levelRaw !== "DANGER" && levelRaw !== "WARNING" && levelRaw !== "INFO") {
    return null;
  }
  const messageRaw = r.message;
  const message = typeof messageRaw === "string" ? messageRaw.trim() : "";
  if (message.length === 0) {
    return null;
  }
  const uf = r.usedFallback !== undefined ? r.usedFallback : r.used_fallback;
  const usedFallback = uf === true;
  return { level: levelRaw, message, usedFallback };
}

export interface JobAnalysisDetail {
  jobId: string;
  jobStatus: string;
  brandName: string;
  errorMessage: string | null;
  brandColor: string;
  logoUrl: string | null;
  project: JobProjectInfo | null;
  jobSummaryDiagnostic?: string | null;
  jobSummaryRecommendedActions?: string[];
  jobMedianModifiedZ?: number | null;
  jobMedianVisibilityStage?: number | null;
  results: ResultDetail[];
  factBasedScore?: number;
  rubricGaps?: string[];
  scoreBreakdown?: ScoreBreakdown | null;
  remediationTasks?: RemediationTask[];
  emotionalAlert?: EmotionalAlertPayload | null;
}

function auditDateString(v: unknown): string | null {
  if (typeof v === "string") {
    return v.length >= 10 ? v.slice(0, 10) : v;
  }
  if (Array.isArray(v) && v.length >= 3) {
    const y = v[0];
    const mo = v[1];
    const d = v[2];
    if (
      typeof y === "number" &&
      typeof mo === "number" &&
      typeof d === "number" &&
      Number.isInteger(y) &&
      Number.isInteger(mo) &&
      Number.isInteger(d)
    ) {
      return `${y}-${String(mo).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
    }
  }
  return null;
}

function gbvsKeysEqual(a: number, b: number): boolean {
  if (Object.is(a, b)) {
    return true;
  }
  return Number.isNaN(a) && Number.isNaN(b);
}

export function withGbvsCompetitionRanks(results: ResultDetail[]): ResultDetail[] {
  if (results.length === 0) {
    return results;
  }
  type Item = { idx: number; r: ResultDetail; key: number };
  const items: Item[] = results.map((r, idx) => {
    const g = r.gbvsNormalizedScore ?? r.somScore;
    const key = typeof g === "number" && !Number.isNaN(g) ? g : Number.NEGATIVE_INFINITY;
    return { idx, r, key };
  });
  items.sort((a, b) => {
    if (a.key > b.key) {
      return -1;
    }
    if (a.key < b.key) {
      return 1;
    }
    return a.r.query.localeCompare(b.r.query);
  });
  const rankAt: number[] = new Array(results.length);
  let p = 0;
  while (p < items.length) {
    const runStart = p;
    const k = items[p].key;
    while (p < items.length && gbvsKeysEqual(items[p].key, k)) {
      p++;
    }
    const rank = runStart + 1;
    for (let j = runStart; j < p; j++) {
      rankAt[items[j].idx] = rank;
    }
  }
  return results.map((r, i) => ({ ...r, mentionRank: rankAt[i] }));
}

export function parseResultDetail(raw: unknown): ResultDetail | null {
  if (raw === null || typeof raw !== "object") {
    return null;
  }
  const r = raw as JsonDict;
  const resultId = typeof r.resultId === "string" ? r.resultId : undefined;
  const query = typeof r.query === "string" ? r.query : undefined;
  const somScore = typeof r.somScore === "number" && !Number.isNaN(r.somScore) ? r.somScore : undefined;
  if (resultId === undefined || query === undefined || somScore === undefined || Number.isNaN(somScore)) {
    return null;
  }
  const rawResponse = typeof r.rawResponse === "string" ? r.rawResponse : undefined;
  const auditRaw = r.auditDate;
  const createdAt = typeof r.createdAt === "string" ? r.createdAt : undefined;
  const auditDate = auditDateString(auditRaw);
  if (rawResponse === undefined || auditDate === null || createdAt === undefined) {
    return null;
  }
  const mrRaw = r.mentionRank;
  const mentionRank =
    mrRaw === null || mrRaw === undefined
      ? null
      : typeof mrRaw === "number" && !Number.isNaN(mrRaw)
        ? mrRaw
        : null;
  const osRaw = r.overallScore;
  const overallScore =
    osRaw === null || osRaw === undefined
      ? null
      : typeof osRaw === "number" && !Number.isNaN(osRaw)
        ? osRaw
        : null;
  const raRaw = r.recommendedActions;
  const recommendedActions =
    Array.isArray(raRaw) && raRaw.every((x): x is string => typeof x === "string") ? raRaw : undefined;
  const sd = r.significantDeviation;
  const significantDeviation =
    typeof sd === "boolean" ? sd : sd === null || sd === undefined ? null : undefined;
  const gbs = typeof r.gbvsNormalizedScore === "number" && !Number.isNaN(r.gbvsNormalizedScore)
    ? r.gbvsNormalizedScore
    : undefined;
  const tc = typeof r.tokenCount === "number" && !Number.isNaN(r.tokenCount) ? r.tokenCount : undefined;
  const rpRaw = r.aiCitationPosition;
  const aiCitationPosition: number | null =
    rpRaw === null || rpRaw === undefined
      ? null
      : typeof rpRaw === "number" && !Number.isNaN(rpRaw)
        ? rpRaw
        : null;
  const si =
    typeof r.sentimentIntensity === "number" && !Number.isNaN(r.sentimentIntensity)
      ? r.sentimentIntensity
      : undefined;
  const relRaw = r.resolvedEntityLabel;
  const resolvedEntityLabel =
    relRaw === null || relRaw === undefined
      ? null
      : typeof relRaw === "string"
        ? relRaw
        : null;
  const vs = typeof r.visibilityStage === "number" && !Number.isNaN(r.visibilityStage) ? r.visibilityStage : undefined;
  const vsbRaw = r.visibilityStageBand;
  const vsnRaw = r.visibilityStageNarrative;
  const cvRaw = r.calculationVersion;
  const mz =
    typeof r.modifiedZScore === "number" && !Number.isNaN(r.modifiedZScore) ? r.modifiedZScore : undefined;
  const dmRaw = r.diagnosticMessage;
  const diagnosticMessage =
    dmRaw === null || dmRaw === undefined
      ? null
      : typeof dmRaw === "string"
        ? dmRaw
        : null;
  const naRaw = r.negativeAlert;
  return {
    resultId,
    query,
    somScore,
    gbvsNormalizedScore: gbs !== undefined && !Number.isNaN(gbs) ? gbs : undefined,
    brandMentioned: r.brandMentioned === true,
    mentionRank,
    overallScore,
    tokenCount: tc !== undefined && !Number.isNaN(tc) ? tc : undefined,
    aiCitationPosition,
    sentimentIntensity: si !== undefined && !Number.isNaN(si) ? si : undefined,
    resolvedEntityLabel,
    visibilityStage: vs !== undefined && !Number.isNaN(vs) ? vs : null,
    visibilityStageBand: typeof vsbRaw === "string" ? vsbRaw : undefined,
    visibilityStageNarrative: typeof vsnRaw === "string" ? vsnRaw : undefined,
    calculationVersion: typeof cvRaw === "string" ? cvRaw : undefined,
    negativeAlert: naRaw === true,
    modifiedZScore: mz !== undefined && !Number.isNaN(mz) ? mz : null,
    diagnosticMessage,
    recommendedActions,
    significantDeviation,
    rawResponse,
    auditDate,
    createdAt,
  };
}

export function extractApiErrorMessage(parsed: unknown): string | undefined {
  if (parsed === null || typeof parsed !== "object") {
    return undefined;
  }
  const r = parsed as JsonDict;
  const v = r.errorMessage;
  return typeof v === "string" ? v : undefined;
}

export function mergeJobAnalysisWithPdfContext(data: JobAnalysisDetail): JobAnalysisDetail {
  if (typeof window === "undefined") {
    return data;
  }
  const w = window as unknown as { __GEO_PDF_CONTEXT__?: unknown };
  const ctx = w.__GEO_PDF_CONTEXT__;
  if (ctx === null || ctx === undefined || typeof ctx !== "object") {
    return data;
  }
  const c = ctx as Record<string, unknown>;
  const next: JobAnalysisDetail = { ...data };
  if (typeof c.jobSummaryDiagnostic === "string") {
    next.jobSummaryDiagnostic = c.jobSummaryDiagnostic;
  }
  const acts = c.jobSummaryRecommendedActions;
  if (Array.isArray(acts) && acts.every((x): x is string => typeof x === "string")) {
    next.jobSummaryRecommendedActions = acts;
  }
  if (typeof c.jobMedianModifiedZ === "number" && !Number.isNaN(c.jobMedianModifiedZ)) {
    next.jobMedianModifiedZ = c.jobMedianModifiedZ;
  }
  if (typeof c.jobMedianVisibilityStage === "number" && !Number.isNaN(c.jobMedianVisibilityStage)) {
    next.jobMedianVisibilityStage = c.jobMedianVisibilityStage;
  }
  return next;
}

export function parseJobAnalysisDetail(raw: unknown): JobAnalysisDetail | null {
  if (raw === null || typeof raw !== "object") {
    return null;
  }
  const r = raw as JsonDict;
  const jobId = typeof r.jobId === "string" ? r.jobId : undefined;
  const jobStatus = typeof r.jobStatus === "string" ? r.jobStatus : undefined;
  const brandName = typeof r.brandName === "string" ? r.brandName : undefined;
  if (jobId === undefined || jobStatus === undefined || brandName === undefined) {
    return null;
  }
  const err =
    r.errorMessage === null || r.errorMessage === undefined
      ? null
      : typeof r.errorMessage === "string"
        ? r.errorMessage
        : null;
  const bc =
    typeof r.brandColor === "string" && r.brandColor.length > 0
      ? r.brandColor
      : "#4F46E5";
  const lu =
    r.logoUrl === null || r.logoUrl === undefined
      ? null
      : typeof r.logoUrl === "string" && r.logoUrl.length > 0
        ? r.logoUrl
        : null;
  const resultsRaw = Array.isArray(r.results) ? r.results : [];
  const results = withGbvsCompetitionRanks(
    resultsRaw
      .map((item) => parseResultDetail(item))
      .filter((x): x is ResultDetail => x !== null),
  );
  const jsd =
    r.jobSummaryDiagnostic === undefined || r.jobSummaryDiagnostic === null
      ? null
      : typeof r.jobSummaryDiagnostic === "string"
        ? r.jobSummaryDiagnostic
        : null;
  const jsraRaw = r.jobSummaryRecommendedActions;
  const jobSummaryRecommendedActions =
    Array.isArray(jsraRaw) && jsraRaw.every((x): x is string => typeof x === "string") ? jsraRaw : [];
  const jmz =
    r.jobMedianModifiedZ === undefined || r.jobMedianModifiedZ === null
      ? null
      : typeof r.jobMedianModifiedZ === "number" && !Number.isNaN(r.jobMedianModifiedZ)
        ? r.jobMedianModifiedZ
        : null;
  const jmvs =
    r.jobMedianVisibilityStage === undefined || r.jobMedianVisibilityStage === null
      ? null
      : typeof r.jobMedianVisibilityStage === "number" && !Number.isNaN(r.jobMedianVisibilityStage)
        ? r.jobMedianVisibilityStage
        : null;
  const fbsRaw = r.factBasedScore ?? r.fact_based_score;
  const factBasedScore =
    typeof fbsRaw === "number" && !Number.isNaN(fbsRaw) ? fbsRaw : undefined;
  const rgRaw = r.rubricGaps ?? r.rubric_gaps;
  const rubricGaps =
    Array.isArray(rgRaw) && rgRaw.every((x): x is string => typeof x === "string") ? rgRaw : undefined;
  const scoreBreakdown = parseScoreBreakdown(r.scoreBreakdown ?? r.score_breakdown);
  const remediationTasks = parseRemediationTasks(r.remediationTasks ?? r.remediation_tasks);
  const emotionalAlertParsed = parseEmotionalAlertPayload(r.emotional_alert ?? r.emotionalAlert);
  return {
    jobId,
    jobStatus,
    brandName,
    errorMessage: err,
    brandColor: bc,
    logoUrl: lu,
    project: parseJobProjectInfo(r.project),
    jobSummaryDiagnostic: jsd,
    jobSummaryRecommendedActions,
    jobMedianModifiedZ: jmz,
    jobMedianVisibilityStage: jmvs,
    results,
    factBasedScore,
    rubricGaps,
    scoreBreakdown,
    remediationTasks,
    ...(emotionalAlertParsed !== null ? { emotionalAlert: emotionalAlertParsed } : {}),
  };
}

function remediationCapsFromPriority(
  priority: RemediationTaskPriority,
): { level: number; requiredScoreThreshold: number } {
  if (priority === "B") {
    return { level: 1, requiredScoreThreshold: 0 };
  }
  if (priority === "A") {
    return { level: 2, requiredScoreThreshold: 60 };
  }
  return { level: 3, requiredScoreThreshold: 80 };
}

function parseScoreBreakdown(raw: unknown): ScoreBreakdown | null {
  if (raw === null || raw === undefined || typeof raw !== "object") {
    return null;
  }
  const r = raw as JsonDict;
  const ai = pickNum(r, "aiAuditTotal", "ai_audit_total");
  const meo = pickNum(r, "meoTotal", "meo_total");
  const mr = pickNum(r, "machineReadabilityTotal", "machine_readability_total");
  const finalScore = pickNum(r, "finalScore", "final_score");
  if (ai === undefined || meo === undefined || mr === undefined || finalScore === undefined) {
    return null;
  }
  return {
    aiAuditTotal: ai,
    meoTotal: meo,
    machineReadabilityTotal: mr,
    finalScore,
  };
}

export function parseRemediationTaskItem(item: unknown): RemediationTask | null {
  if (item === null || typeof item !== "object") {
    return null;
  }
  const r = item as JsonDict;
  const id = typeof r.id === "string" ? r.id : undefined;
  const categoryRaw = typeof r.category === "string" ? r.category : undefined;
  const priorityRaw = typeof r.priority === "string" ? r.priority : undefined;
  const title = typeof r.title === "string" ? r.title : undefined;
  const content = typeof r.content === "string" ? r.content : undefined;
  const impactRaw = pickNum(r, "impactScore", "impact_score");
  if (
    id === undefined ||
    categoryRaw === undefined ||
    priorityRaw === undefined ||
    title === undefined ||
    content === undefined ||
    impactRaw === undefined
  ) {
    return null;
  }
  if (categoryRaw !== "SPIKE" && categoryRaw !== "SLAB") {
    return null;
  }
  if (priorityRaw !== "S" && priorityRaw !== "A" && priorityRaw !== "B") {
    return null;
  }
  const tsRaw = r.targetSection !== undefined ? r.targetSection : r.target_section;
  const sectionTrimmed =
    typeof tsRaw === "string" ? tsRaw.trim() : "";
  const caps = remediationCapsFromPriority(priorityRaw);
  const levelParsed = pickNum(r, "level", "level");
  const thresholdParsed = pickNum(r, "requiredScoreThreshold", "required_score_threshold");
  const level = levelParsed !== undefined ? levelParsed : caps.level;
  const requiredScoreThreshold =
    thresholdParsed !== undefined ? thresholdParsed : caps.requiredScoreThreshold;
  const maskedRaw = pickBool(r, "isMasked", "is_masked");
  const isMasked = maskedRaw === true;
  const targetSectionPayload =
    sectionTrimmed.length > 0 ? ({ targetSection: sectionTrimmed } as const) : ({} as const);
  return {
    id,
    category: categoryRaw,
    priority: priorityRaw,
    title,
    content,
    impactScore: impactRaw,
    level,
    requiredScoreThreshold,
    isMasked,
    ...targetSectionPayload,
  };
}

export function parseTaskToneRegenerateEnvelope(raw: unknown): RemediationTask | null {
  if (raw === null || typeof raw !== "object") {
    return null;
  }
  const r = raw as JsonDict;
  const t = r.task;
  return parseRemediationTaskItem(t);
}

function parseRemediationTasks(raw: unknown): RemediationTask[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  const out: RemediationTask[] = [];
  for (const item of raw) {
    const parsed = parseRemediationTaskItem(item);
    if (parsed !== null) {
      out.push(parsed);
    }
  }
  return out;
}
