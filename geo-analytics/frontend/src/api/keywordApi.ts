import type {
  KeywordRegistrationRequestPayload,
  KeywordRegistrationResult,
  KeywordSuggestionRequest,
  KeywordSuggestionResponse,
  SelectedKeywordPayload,
} from "../types/keyword";

function extractErrorMessage(text: string): string {
  if (!text) return "リクエストに失敗しました";
  try {
    const o = JSON.parse(text) as unknown;
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
): Promise<KeywordSuggestionResponse> {
  const body: KeywordSuggestionRequest = { url: url.trim(), target_description: targetDescription.trim() };
  const res = await fetch("/api/v1/keywords/suggest", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(extractErrorMessage(text));
  }
  const parsed: unknown = text ? JSON.parse(text) : null;
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
  const res = await fetch(`/api/v1/projects/${encodeURIComponent(projectId)}/keywords/batch`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(extractErrorMessage(text));
  }
  const parsed: unknown = text ? JSON.parse(text) : null;
  if (
    typeof parsed !== "object" ||
    parsed === null ||
    !("registered_count" in parsed) ||
    !("skipped_count" in parsed)
  ) {
    throw new Error("登録レスポンス形式が不正です");
  }
  const o = parsed as Record<string, unknown>;
  const rc = o.registered_count;
  const sc = o.skipped_count;
  if (typeof rc !== "number" || typeof sc !== "number") {
    throw new Error("登録レスポンス形式が不正です");
  }
  return { registered_count: rc, skipped_count: sc };
}
