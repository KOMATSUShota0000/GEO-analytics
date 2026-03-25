export type KeywordSuggestionRequest = { url: string; target_description: string };
export type KeywordCategory = { category_name: string; keywords: string[] };
export type KeywordSuggestionResponse = { categories: KeywordCategory[] };
export type SelectedKeywordPayload = { text: string; category_name: string };
export type KeywordRegistrationRequestPayload = { project_id: string; keywords: SelectedKeywordPayload[] };
export type KeywordRegistrationResult = { registered_count: number; skipped_count: number };
