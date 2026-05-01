export type QueryProposalKnowledgeRequest = {
  businessDescription: string;
  targetAudience: string;
  strategicFocus: string;
};

export type QueryProposalRequest = {
  url: string;
  knowledge: QueryProposalKnowledgeRequest;
};

export type SuggestedQuery = {
  queryText: string;
  intent: string;
};

export type QueryProposalResponse = {
  inferredPersona: string;
  queries: SuggestedQuery[];
};

export type QueryProposalPhase = "SCRAPING" | "AI_ANALYSIS" | "VALIDATION";

export type ApiErrorResponse = {
  errorCode: string;
  message: string;
  details?: Record<string, unknown>;
};
