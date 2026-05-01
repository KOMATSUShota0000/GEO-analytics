import { GeoApiRequestError } from "../errors/GeoApiRequestError";
import type { QueryProposalRequest, QueryProposalResponse, SuggestedQuery } from "../types/queryProposal";
import type { SubscriptionPlanApi } from "../types/analysis";
import { apiFetch, parseJsonTextAsCamel, responseJsonAsCamel } from "./apiFetch";

const PROPOSAL_TIMEOUT_MS = 60_000;

function asSuggestedQuery(value: unknown): SuggestedQuery | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const o = value as Record<string, unknown>;
  if (typeof o.queryText !== "string" || typeof o.intent !== "string") {
    return null;
  }
  return { queryText: o.queryText, intent: o.intent };
}

function asQueryProposalResponse(value: unknown): QueryProposalResponse | null {
  if (value === null || typeof value !== "object") {
    return null;
  }
  const o = value as Record<string, unknown>;
  if (typeof o.id !== "string" || o.id.trim().length === 0) {
    return null;
  }
  if (typeof o.inferredPersona !== "string" || !Array.isArray(o.queries)) {
    return null;
  }
  const queries: SuggestedQuery[] = [];
  for (const item of o.queries) {
    const row = asSuggestedQuery(item);
    if (row === null) {
      return null;
    }
    queries.push(row);
  }
  return { id: o.id.trim(), inferredPersona: o.inferredPersona, queries };
}

function asConvertProposalResponse(value: unknown): string | null {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return null;
  }
  const o = value as Record<string, unknown>;
  if (typeof o.jobId !== "string" || o.jobId.trim().length === 0) {
    return null;
  }
  return o.jobId.trim();
}

function toSnakeCaseBody(request: QueryProposalRequest): Record<string, unknown> {
  const k = request.knowledge;
  return {
    url: request.url.trim(),
    knowledge: {
      business_description: k.businessDescription,
      target_audience: k.targetAudience,
      strategic_focus: k.strategicFocus,
    },
  };
}

function asErrorDetails(value: unknown): Record<string, unknown> | undefined {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return undefined;
  }
  return value as Record<string, unknown>;
}

async function throwGeoApiRequestError(res: Response): Promise<never> {
  const text = await res.text();
  let errorCode = "unknown";
  let message = text.trim().length > 0 ? text : `HTTP ${res.status}`;
  let details: Record<string, unknown> | undefined;
  try {
    if (text.trim().length > 0) {
      const parsed = parseJsonTextAsCamel(text);
      if (parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)) {
        const o = parsed as Record<string, unknown>;
        if (typeof o.errorCode === "string") {
          errorCode = o.errorCode;
        }
        if (typeof o.message === "string") {
          message = o.message;
        }
        details = asErrorDetails(o.details);
      }
    }
  } catch {
    /* 本文が JSON でない場合はフォールバックメッセージのまま */
  }
  throw new GeoApiRequestError(res.status, errorCode, message, details);
}

/**
 * GEO 向けクエリ案を同期生成する。認証・CSRF は {@link apiFetch} に委譲する。
 * 60 秒でタイムアウト。{@link signal} が渡された場合は、どちらか先に中断された方で打ち切る。
 */
export async function postQueryProposal(
  request: QueryProposalRequest,
  signal?: AbortSignal,
): Promise<QueryProposalResponse> {
  const combined = new AbortController();
  const timer = setTimeout(() => {
    combined.abort();
  }, PROPOSAL_TIMEOUT_MS);

  const forwardAbort = (): void => {
    combined.abort();
  };

  if (signal !== undefined) {
    if (signal.aborted) {
      forwardAbort();
    } else {
      signal.addEventListener("abort", forwardAbort, { once: true });
    }
  }

  try {
    const res = await apiFetch("/api/v1/proposals", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(toSnakeCaseBody(request)),
      signal: combined.signal,
    });

    if (!res.ok) {
      await throwGeoApiRequestError(res);
    }

    const parsed = await responseJsonAsCamel(res);
    const data = asQueryProposalResponse(parsed);
    if (data === null) {
      throw new Error("クエリ提案の応答形式が不正です");
    }
    return data;
  } finally {
    clearTimeout(timer);
    if (signal !== undefined) {
      signal.removeEventListener("abort", forwardAbort);
    }
  }
}

/**
 * 保存済みクエリ提案をジョブに変換し、生成されたジョブ ID を返す。
 */
export async function convertProposalToJob(id: string, plan: SubscriptionPlanApi): Promise<string> {
  const trimmedId = id.trim();
  if (trimmedId.length === 0) {
    throw new Error("proposal id must not be blank");
  }
  const res = await apiFetch(`/api/v1/proposals/${encodeURIComponent(trimmedId)}/convert`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ plan }),
  });
  if (!res.ok) {
    await throwGeoApiRequestError(res);
  }
  const parsed = await responseJsonAsCamel(res);
  const jobId = asConvertProposalResponse(parsed);
  if (jobId === null) {
    throw new Error("ジョブ作成の応答形式が不正です");
  }
  return jobId;
}
