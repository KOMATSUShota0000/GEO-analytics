import { apiFetch, parseJsonTextAsCamel, responseJsonAsCamel } from "./apiFetch";

const EXTRACT_TIMEOUT_MS = 60000;

export type MinorityReportItem = {
  insight: string;
  conflictReason: string;
  evidence: string;
};

export type ProjectContextData = {
  industryType: string;
  strengths: string[];
  targetAudience: string;
  minorityReports: MinorityReportItem[];
};

function asMinorityReportItem(o: unknown): MinorityReportItem {
  if (o === null || typeof o !== "object") {
    return { insight: "", conflictReason: "", evidence: "" };
  }
  const r = o as Record<string, unknown>;
  return {
    insight: typeof r.insight === "string" ? r.insight : "",
    conflictReason: typeof r.conflictReason === "string" ? r.conflictReason : "",
    evidence: typeof r.evidence === "string" ? r.evidence : "",
  };
}

function asMinorityReports(raw: unknown): MinorityReportItem[] {
  if (raw === undefined) {
    return [];
  }
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((it) => asMinorityReportItem(it));
}

function asContextData(raw: unknown): ProjectContextData | null {
  if (raw === null || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  if (typeof o.industryType !== "string") return null;
  if (!Array.isArray(o.strengths)) return null;
  const strengths: string[] = [];
  for (const s of o.strengths) {
    if (typeof s !== "string") return null;
    strengths.push(s);
  }
  if (typeof o.targetAudience !== "string") return null;
  return {
    industryType: o.industryType,
    strengths,
    targetAudience: o.targetAudience,
    minorityReports: asMinorityReports(o.minorityReports),
  };
}

export async function postExtractContext(
  projectId: string,
  url: string,
): Promise<ProjectContextData> {
  const controller = new AbortController();
  const timer = setTimeout(() => {
    controller.abort();
  }, EXTRACT_TIMEOUT_MS);
  try {
    const res = await apiFetch(
      `/api/v1/projects/${encodeURIComponent(projectId)}/extract-context`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: url.trim() }),
        signal: controller.signal,
      },
    );
    if (!res.ok) {
      const errText = await res.text();
      const err = new Error(errText || `HTTP ${res.status}`) as Error & { status?: number; body?: string };
      err.status = res.status;
      err.body = errText;
      throw err;
    }
    const parsed = await responseJsonAsCamel(res);
    const data = asContextData(parsed);
    if (!data) {
      throw new Error("解析結果の形式が不正です");
    }
    return data;
  } finally {
    clearTimeout(timer);
  }
}

export async function patchProjectContext(
  projectId: string,
  body: {
    industryType: string;
    extractedStrengths: string;
    targetAudience: string;
    minorityReports: MinorityReportItem[];
  },
): Promise<ProjectContextData> {
  const res = await apiFetch(
    `/api/v1/projects/${encodeURIComponent(projectId)}/context`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        industry_type: body.industryType,
        extracted_strengths: body.extractedStrengths,
        target_audience: body.targetAudience,
        minority_reports: body.minorityReports.map((m) => ({
          insight: m.insight,
          conflict_reason: m.conflictReason,
          evidence: m.evidence,
        })),
      }),
    },
  );
  if (!res.ok) {
    const errText = await res.text();
    const err = new Error(errText || `HTTP ${res.status}`) as Error & { status?: number; body?: string };
    err.status = res.status;
    err.body = errText;
    throw err;
  }
  const parsed = await responseJsonAsCamel(res);
  const data = asContextData(parsed);
  if (!data) {
    throw new Error("保存後の形式が不正です");
  }
  return data;
}

export function parseApiErrorBody(
  text: string,
):
  | { errorCode: string; message: string; fields: Record<string, string> }
  | null {
  if (text.trim().length === 0) {
    return null;
  }
  try {
    const p = parseJsonTextAsCamel(text) as {
      errorCode?: string;
      message?: string;
      details?: { fields?: Record<string, string> };
    };
    if (p.errorCode !== "validation_failed" || typeof p.message !== "string") {
      return null;
    }
    const fields = p.details?.fields;
    if (fields === undefined || fields === null || typeof fields !== "object") {
      return { errorCode: p.errorCode, message: p.message, fields: {} };
    }
    return { errorCode: p.errorCode, message: p.message, fields: { ...fields } };
  } catch {
    return null;
  }
}
