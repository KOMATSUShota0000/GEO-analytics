import { apiFetch, responseJsonAsCamel } from "./apiFetch";
import {
  normalizeJobStatusResponse,
  parseJobAnalysisDetail,
  type JobAnalysisDetail,
  type JobStatusResponse,
} from "../types/analysis";

export async function getJobStatus(jobId: string, signal?: AbortSignal): Promise<JobStatusResponse | null> {
  const trimmed = jobId.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const response = await apiFetch(`/api/v1/jobs/${encodeURIComponent(trimmed)}`, { signal });
  if (!response.ok) {
    return null;
  }
  const body = await responseJsonAsCamel(response);
  return normalizeJobStatusResponse(body);
}

export async function getJobAnalysis(jobId: string, signal?: AbortSignal): Promise<JobAnalysisDetail | null> {
  const trimmed = jobId.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const response = await apiFetch(`/api/v1/jobs/${encodeURIComponent(trimmed)}/analysis`, { signal });
  if (!response.ok) {
    return null;
  }
  const body = await responseJsonAsCamel(response);
  return parseJobAnalysisDetail(body);
}
