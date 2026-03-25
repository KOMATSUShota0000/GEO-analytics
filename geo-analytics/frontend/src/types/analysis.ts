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
  jobStatus: string;
  brandName: string;
  errorMessage: string | null;
  pdfStatus: string | null;
  pdfFilePath: string | null;
  createdAt: string;
  updatedAt: string;
}

export function normalizeJobStatusResponse(value: unknown): JobStatusResponse | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const r = value as Record<string, unknown>;
  if (typeof r.jobId !== "string") {
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
  return {
    jobId: r.jobId,
    jobStatus: r.jobStatus,
    brandName: r.brandName,
    errorMessage:
      r.errorMessage === undefined || r.errorMessage === null ? null : r.errorMessage,
    pdfStatus: r.pdfStatus === undefined || r.pdfStatus === null ? null : r.pdfStatus,
    pdfFilePath:
      r.pdfFilePath === undefined || r.pdfFilePath === null ? null : r.pdfFilePath,
    createdAt: r.createdAt,
    updatedAt: r.updatedAt,
  };
}

export interface JobProjectInfo {
  projectId: string;
  projectName: string;
  targetUrl: string;
  competitorUrls: string[];
}

export interface VerifyStreamChunkPayload {
  kind: "delta" | "done" | "error";
  text: string;
  queryId?: string;
}

export interface SseStreamErrorBody {
  message: string;
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

export interface ResultDetail {
  resultId: string;
  query: string;
  somScore: number;
  brandMentioned: boolean;
  mentionRank: number | null;
  overallScore: number | null;
  rawResponse: string;
  auditDate: string;
  createdAt: string;
}

export interface JobAnalysisDetail {
  jobId: string;
  jobStatus: string;
  brandName: string;
  errorMessage: string | null;
  project: JobProjectInfo | null;
  results: ResultDetail[];
}
