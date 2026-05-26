export type CompetitorExtractionMode = "LOCAL_STORE" | "CORPORATE_SERVICE" | "ONLINE_SERVICE";

export type CreateJobRequestPayload = {
  brandName: string;
  targetUrl: string;
  industryType?: CompetitorExtractionMode;
  businessSummary?: string;
  targetAudience?: string;
  focusPoints?: string;
  files?: File[];
};

function optionalTrim(value: string | undefined): string | undefined {
  if (value === undefined) {
    return undefined;
  }
  const t = value.trim();
  return t === "" ? undefined : t;
}

export function buildCreateJobBody(input: CreateJobRequestPayload): Record<string, unknown> {
  const out: Record<string, unknown> = {
    brandName: input.brandName.trim(),
    targetUrl: input.targetUrl.trim(),
    industryType: input.industryType ?? "LOCAL_STORE",
  };
  const businessSummary = optionalTrim(input.businessSummary);
  const targetAudience = optionalTrim(input.targetAudience);
  const focusPoints = optionalTrim(input.focusPoints);
  if (businessSummary !== undefined) {
    out.businessSummary = businessSummary;
  }
  if (targetAudience !== undefined) {
    out.targetAudience = targetAudience;
  }
  if (focusPoints !== undefined) {
    out.focusPoints = focusPoints;
  }
  return out;
}
