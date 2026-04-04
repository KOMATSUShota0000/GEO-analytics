export type KeywordSuggestionRequest = {
  url: string;
  target_description: string;
  registered_keywords?: string[];
};
export type KeywordCategory = { categoryName: string; keywords: string[] };
export type KeywordSuggestionResponse = { categories: KeywordCategory[] };
export type SelectedKeywordPayload = { text: string; category_name: string };
export type KeywordRegistrationRequestPayload = { project_id: string; keywords: SelectedKeywordPayload[] };
export type KeywordRegistrationResult = { registeredCount: number; skippedCount: number };
