package com.geo.analytics.infrastructure.api;

/**
 * @param formattedBlock          hybrid ND-JSON lines for the user message
 * @param useSerpRagSystemPrompt true when SerpAPI succeeded and strict grounding system text must be used
 */
public record SerpRagBundle(String formattedBlock, boolean useSerpRagSystemPrompt) {
}
