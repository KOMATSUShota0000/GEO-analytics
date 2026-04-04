import type {
  KeywordRegistrationRequestPayload,
  KeywordRegistrationResult,
  KeywordSuggestionRequest,
  KeywordSuggestionResponse,
  SelectedKeywordPayload,
} from "../types/keyword";
import { apiFetch, parseJsonTextAsCamel } from "./apiFetch";

function extractErrorMessage(text: string): string {
  if (!text) return "リクエストに失敗しました";
  try {
    const o = parseJsonTextAsCamel(text) as unknown;
    if (
      typeof o === "object" &&
      o !== null &&
      "detail" in o &&
      typeof (o as { detail: unknown }).detail === "string"
    ) {
      return (o as { detail: string }).detail;
    }
  } catch {}
  return text.length > 280 ? `${text.slice(0, 280)}…` : text;
}

export async function suggestKeywords(
  url: string,
  targetDescription: string,
  registeredKeywords?: string[],
): Promise<KeywordSuggestionResponse> {
  const body: KeywordSuggestionRequest = {
    url: url.trim(),
    target_description: targetDescription.trim(),
    ...(registeredKeywords !== undefined && registeredKeywords.length > 0
      ? { registered_keywords: registeredKeywords }
      : {}),
  };
  const res = await apiFetch("/api/v1/keywords/suggest", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(extractErrorMessage(text));
  }
  const parsed: unknown = text ? parseJsonTextAsCamel(text) : null;
  if (typeof parsed !== "object" || parsed === null || !("categories" in parsed)) {
    throw new Error("レスポンス形式が不正です");
  }
  return parsed as KeywordSuggestionResponse;
}

export async function registerProjectKeywords(
  projectId: string,
  keywords: SelectedKeywordPayload[],
): Promise<KeywordRegistrationResult> {
  const body: KeywordRegistrationRequestPayload = { project_id: projectId, keywords };
  const res = await apiFetch(`/api/v1/projects/${encodeURIComponent(projectId)}/keywords/batch`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(extractErrorMessage(text));
  }
  const parsed: unknown = text ? parseJsonTextAsCamel(text) : null;
  if (
    typeof parsed !== "object" ||
    parsed === null ||
    !("registeredCount" in parsed) ||
    !("skippedCount" in parsed)
  ) {
    throw new Error("登録レスポンス形式が不正です");
  }
  const o = parsed as Record<string, unknown>;
  const rc = o.registeredCount;
  const sc = o.skippedCount;
  if (typeof rc !== "number" || typeof sc !== "number") {
    throw new Error("登録レスポンス形式が不正です");
  }
  return { registeredCount: rc, skippedCount: sc };
}
