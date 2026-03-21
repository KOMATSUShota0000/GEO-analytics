export interface JobStatusResponse {
  jobId: string;
  jobStatus: string;
  brandName: string;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
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
