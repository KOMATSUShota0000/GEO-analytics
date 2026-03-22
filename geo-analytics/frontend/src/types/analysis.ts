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

export interface ResultDetail {
  resultId: string;
  query: string;
  somScore: number;
  brandMentioned: boolean;
  mentionRank: number | null;
  rawResponse: string;
  createdAt: string;
}

export interface JobAnalysisDetail {
  jobId: string;
  jobStatus: string;
  brandName: string;
  errorMessage: string | null;
  results: ResultDetail[];
}
