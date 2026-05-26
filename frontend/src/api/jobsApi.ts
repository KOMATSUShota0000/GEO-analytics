import { apiFetch, parseJsonTextAsCamel, responseJsonAsCamel } from "./apiFetch";
import { buildCreateJobBody, type CreateJobRequestPayload } from "../types/createJobRequest";
import {
  extractApiErrorCode,
  extractApiErrorMessage,
  normalizeJobStatusResponse,
  parseJobAnalysisDetail,
  type JobAnalysisDetail,
  type JobStatusResponse,
} from "../types/analysis";

export class CreateJobHttpError extends Error {
  readonly status: number;
  readonly errorCode: string | undefined;

  constructor(message: string, status: number, errorCode?: string) {
    super(message);
    this.name = "CreateJobHttpError";
    this.status = status;
    this.errorCode = errorCode;
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

function buildCreateJobFormData(payload: CreateJobRequestPayload): FormData {
  const formData = new FormData();
  const bodyObj = buildCreateJobBody(payload);
  formData.append(
    "request",
    new Blob([JSON.stringify(bodyObj)], { type: "application/json" }),
    "request.json",
  );
  if (payload.files !== undefined) {
    for (const file of payload.files) {
      formData.append("files", file);
    }
  }
  return formData;
}

export async function createJob(
  payload: CreateJobRequestPayload,
  signal?: AbortSignal,
): Promise<JobStatusResponse> {
  const formData = buildCreateJobFormData(payload);
  const response = await apiFetch("/api/v1/jobs", {
    method: "POST",
    body: formData,
    signal,
  });
  if (!response.ok) {
    const text = await response.text();
    let message = text.trim().length > 0 ? text : `HTTP ${response.status}`;
    let errorCode: string | undefined;
    try {
      const parsed = parseJsonTextAsCamel(text);
      errorCode = extractApiErrorCode(parsed);
      const extracted = extractApiErrorMessage(parsed);
      if (extracted !== undefined) {
        message = extracted;
      }
    } catch {
    }
    throw new CreateJobHttpError(message, response.status, errorCode);
  }
  const parsed: unknown = await responseJsonAsCamel(response);
  const created = normalizeJobStatusResponse(parsed);
  if (created === null) {
    throw new Error("ジョブ作成レスポンスの形式が不正です");
  }
  return created;
}

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
