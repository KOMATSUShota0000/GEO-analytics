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
  const r = value as Record<string, unknown>;
  if (typeof r.jobId !== "string") {
    return null;
  }
  if (
    r.projectId !== undefined &&
    r.projectId !== null &&
    typeof r.projectId !== "string"
  ) {
    return null;
  }
  if (typeof r.jobStatus !== "string") {
    return null;
  }
  if (typeof r.brandName !== "string") {
    return null;
  }
  if (
    r.errorMessage !== undefined &&
    r.errorMessage !== null &&
    typeof r.errorMessage !== "string"
  ) {
    return null;
  }
  if (
    r.pdfStatus !== undefined &&
    r.pdfStatus !== null &&
    typeof r.pdfStatus !== "string"
  ) {
    return null;
  }
  if (
    r.pdfFilePath !== undefined &&
    r.pdfFilePath !== null &&
    typeof r.pdfFilePath !== "string"
  ) {
    return null;
  }
  if (typeof r.createdAt !== "string" || typeof r.updatedAt !== "string") {
    return null;
  }
  const dm =
    r.diagnosticMessage === undefined || r.diagnosticMessage === null
      ? null
      : typeof r.diagnosticMessage === "string"
        ? r.diagnosticMessage
        : null;
  const raRaw = r.recommendedActions;
  const recommendedActions =
    Array.isArray(raRaw) && raRaw.every((x): x is string => typeof x === "string")
      ? raRaw
      : [];
  const jmz =
    r.jobMedianModifiedZ === undefined || r.jobMedianModifiedZ === null
      ? null
      : typeof r.jobMedianModifiedZ === "number" && !Number.isNaN(r.jobMedianModifiedZ)
        ? r.jobMedianModifiedZ
        : null;
  return {
    jobId: r.jobId,
    projectId:
      r.projectId === undefined || r.projectId === null || typeof r.projectId !== "string"
        ? null
        : r.projectId,
    jobStatus: r.jobStatus,
    brandName: r.brandName,
    errorMessage:
      r.errorMessage === undefined || r.errorMessage === null ? null : r.errorMessage,
    pdfStatus: r.pdfStatus === undefined || r.pdfStatus === null ? null : r.pdfStatus,
    pdfFilePath:
      r.pdfFilePath === undefined || r.pdfFilePath === null ? null : r.pdfFilePath,
    createdAt: r.createdAt,
    updatedAt: r.updatedAt,
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
  const trendRaw = r.trend_data;
  const shareRaw = r.competitor_shares;
  const planRaw = r.subscription_plan;
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
    const ad = o.audit_date;
    const som = o.average_som_score;
    const ov = o.average_overall_score;
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
}
export function parseJobProjectInfo(raw: unknown): JobProjectInfo | null {
  if (raw === null || typeof raw !== "object") {
    return null;
  }
  const p = raw as Record<string, unknown>;
  if (
    typeof p.projectId !== "string" ||
    typeof p.projectName !== "string" ||
    typeof p.targetUrl !== "string"
  ) {
    return null;
  }
  const comp = Array.isArray(p.competitorUrls)
    ? p.competitorUrls.filter((x): x is string => typeof x === "string")
    : [];
  const bc =
    typeof p.brand_color === "string" && p.brand_color.length > 0
      ? p.brand_color
      : "#4F46E5";
  const lu =
    p.logo_url === null || p.logo_url === undefined
      ? null
      : typeof p.logo_url === "string" && p.logo_url.length > 0
        ? p.logo_url
        : null;
  return {
    projectId: p.projectId,
    projectName: p.projectName,
    targetUrl: p.targetUrl,
    competitorUrls: comp,
    brandColor: bc,
    logoUrl: lu,
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
  const r = parsed as Record<string, unknown>;
  const overallScore = typeof r.overallScore === "number" ? r.overallScore : null;
  const confidenceScore = typeof r.confidenceScore === "number" ? r.confidenceScore : null;
  const somScore = confidenceScore ?? overallScore;
  let brandMentioned: boolean | null = null;
  if (typeof r.brandMentioned === "boolean") {
    brandMentioned = r.brandMentioned;
  }
  let mentionRank: number | null = null;
  if (typeof r.mentionRank === "number" && !Number.isNaN(r.mentionRank)) {
    mentionRank = r.mentionRank;
  }
  return { somScore, overallScore, brandMentioned, mentionRank };
}
function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}
function isoDateFromLocal(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
}
export const MOCK_TREND_LAST_7_DAYS: TrendData[] = (() => {
  const out: TrendData[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const phase = (6 - i) / 6;
    const som = Math.round(38 + phase * 22 + Math.sin(i * 0.7) * 4);
    const overall = Math.round(41 + phase * 18 + Math.cos(i * 0.5) * 3);
    out.push({ date: isoDateFromLocal(d), somScore: som, overallScore: overall });
  }
  return out;
})();
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
  const self = anchorSomScore === null ? 46 : Math.max(18, Math.min(82, Math.round(anchorSomScore)));
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
  if (agg.length >= 2) {
    return agg;
  }
  const live = averageLiveScoresFromParsed(parsedByQueryId);
  if (live !== null && (isStreaming || agg.length === 1)) {
    const base = agg.length === 1 ? [...agg] : MOCK_TREND_LAST_7_DAYS.slice(0, -1);
    const lastDate = isoDateFromLocal(new Date());
    const lastPoint: TrendData = {
      date: lastDate,
      somScore: live.somScore,
      overallScore: live.overallScore,
    };
    if (base.some((p) => p.date === lastDate)) {
      return base.map((p) =>
        p.date === lastDate
          ? { ...p, somScore: lastPoint.somScore, overallScore: lastPoint.overallScore }
          : p,
      );
    }
    return [...base, lastPoint].slice(-7);
  }
  if (agg.length === 1) {
    return [...MOCK_TREND_LAST_7_DAYS.slice(0, -1), agg[0]!];
  }
  return MOCK_TREND_LAST_7_DAYS;
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
  rankPosition?: number;
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
}

export function parseResultDetail(raw: unknown): ResultDetail | null {
  if (raw === null || typeof raw !== "object") {
    return null;
  }
  const r = raw as Record<string, unknown>;
  if (typeof r.resultId !== "string" || typeof r.query !== "string") {
    return null;
  }
  if (typeof r.somScore !== "number" || Number.isNaN(r.somScore)) {
    return null;
  }
  if (typeof r.rawResponse !== "string" || typeof r.auditDate !== "string" || typeof r.createdAt !== "string") {
    return null;
  }
  const mentionRank =
    r.mentionRank === null || r.mentionRank === undefined
      ? null
      : typeof r.mentionRank === "number" && !Number.isNaN(r.mentionRank)
        ? r.mentionRank
        : null;
  const overallScore =
    r.overallScore === null || r.overallScore === undefined
      ? null
      : typeof r.overallScore === "number" && !Number.isNaN(r.overallScore)
        ? r.overallScore
        : null;
  const raRaw = r.recommendedActions;
  const recommendedActions =
    Array.isArray(raRaw) && raRaw.every((x): x is string => typeof x === "string") ? raRaw : undefined;
  const sd = r.significantDeviation;
  const significantDeviation =
    typeof sd === "boolean" ? sd : sd === null || sd === undefined ? null : undefined;
  return {
    resultId: r.resultId,
    query: r.query,
    somScore: r.somScore,
    gbvsNormalizedScore:
      typeof r.gbvsNormalizedScore === "number" && !Number.isNaN(r.gbvsNormalizedScore)
        ? r.gbvsNormalizedScore
        : undefined,
    brandMentioned: r.brandMentioned === true,
    mentionRank,
    overallScore,
    tokenCount:
      typeof r.tokenCount === "number" && !Number.isNaN(r.tokenCount) ? r.tokenCount : undefined,
    rankPosition:
      typeof r.rankPosition === "number" && !Number.isNaN(r.rankPosition) ? r.rankPosition : undefined,
    sentimentIntensity:
      typeof r.sentimentIntensity === "number" && !Number.isNaN(r.sentimentIntensity)
        ? r.sentimentIntensity
        : undefined,
    resolvedEntityLabel:
      r.resolvedEntityLabel === null || r.resolvedEntityLabel === undefined
        ? null
        : typeof r.resolvedEntityLabel === "string"
          ? r.resolvedEntityLabel
          : null,
    visibilityStage:
      typeof r.visibilityStage === "number" && !Number.isNaN(r.visibilityStage)
        ? r.visibilityStage
        : null,
    visibilityStageBand:
      typeof r.visibilityStageBand === "string" ? r.visibilityStageBand : undefined,
    visibilityStageNarrative:
      typeof r.visibilityStageNarrative === "string" ? r.visibilityStageNarrative : undefined,
    calculationVersion:
      typeof r.calculationVersion === "string" ? r.calculationVersion : undefined,
    negativeAlert: r.negativeAlert === true,
    modifiedZScore:
      typeof r.modifiedZScore === "number" && !Number.isNaN(r.modifiedZScore)
        ? r.modifiedZScore
        : null,
    diagnosticMessage:
      r.diagnosticMessage === null || r.diagnosticMessage === undefined
        ? null
        : typeof r.diagnosticMessage === "string"
          ? r.diagnosticMessage
          : null,
    recommendedActions,
    significantDeviation,
    rawResponse: r.rawResponse,
    auditDate: r.auditDate,
    createdAt: r.createdAt,
  };
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
  const r = raw as Record<string, unknown>;
  if (
    typeof r.jobId !== "string" ||
    typeof r.jobStatus !== "string" ||
    typeof r.brandName !== "string"
  ) {
    return null;
  }
  const err =
    r.errorMessage === null || r.errorMessage === undefined
      ? null
      : typeof r.errorMessage === "string"
        ? r.errorMessage
        : typeof r.error_message === "string"
          ? r.error_message
          : null;
  const bc =
    typeof r.brand_color === "string" && r.brand_color.length > 0
      ? r.brand_color
      : "#4F46E5";
  const lu =
    r.logo_url === null || r.logo_url === undefined
      ? null
      : typeof r.logo_url === "string" && r.logo_url.length > 0
        ? r.logo_url
        : null;
  const resultsRaw = Array.isArray(r.results) ? r.results : [];
  const results = resultsRaw
    .map((item) => parseResultDetail(item))
    .filter((x): x is ResultDetail => x !== null);
  const jsd =
    r.job_summary_diagnostic === undefined || r.job_summary_diagnostic === null
      ? null
      : typeof r.job_summary_diagnostic === "string"
        ? r.job_summary_diagnostic
        : null;
  const jsraRaw = r.job_summary_recommended_actions;
  const jobSummaryRecommendedActions =
    Array.isArray(jsraRaw) && jsraRaw.every((x): x is string => typeof x === "string") ? jsraRaw : [];
  const jmz =
    r.job_median_modified_z === undefined || r.job_median_modified_z === null
      ? null
      : typeof r.job_median_modified_z === "number" && !Number.isNaN(r.job_median_modified_z)
        ? r.job_median_modified_z
        : null;
  const jmvs =
    r.job_median_visibility_stage === undefined || r.job_median_visibility_stage === null
      ? null
      : typeof r.job_median_visibility_stage === "number" && !Number.isNaN(r.job_median_visibility_stage)
        ? r.job_median_visibility_stage
        : null;
  return {
    jobId: r.jobId,
    jobStatus: r.jobStatus,
    brandName: r.brandName,
    errorMessage: err,
    brandColor: bc,
    logoUrl: lu,
    project: parseJobProjectInfo(r.project),
    jobSummaryDiagnostic: jsd,
    jobSummaryRecommendedActions,
    jobMedianModifiedZ: jmz,
    jobMedianVisibilityStage: jmvs,
    results,
  };
}
