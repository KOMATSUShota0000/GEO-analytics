package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CompetitorFilterAiSelection;
import com.geo.analytics.application.dto.ExtractedPlace;
import com.geo.analytics.application.dto.SelectedCompetitor;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.infrastructure.ai.CompetitorFilterPrompts;
import com.geo.analytics.infrastructure.api.dto.SerpOrganicResult;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CompetitorFilterService {
    private static final long COMPETITOR_FILTER_CREDIT = 40L;

    private final CreditVaultService creditVaultService;
    private final ChatLanguageModel geminiCompetitorFilterModel;
    private final ObjectMapper objectMapper;
    private final SyntheticSelectedCompetitorFactory syntheticSelectedCompetitorFactory;

    public CompetitorFilterService(
            CreditVaultService creditVaultService,
            @Qualifier(AiConfig.GEMINI_COMPETITOR_FILTER_MODEL) ChatLanguageModel geminiCompetitorFilterModel,
            ObjectMapper objectMapper,
            SyntheticSelectedCompetitorFactory syntheticSelectedCompetitorFactory) {
        this.creditVaultService = creditVaultService;
        this.geminiCompetitorFilterModel = geminiCompetitorFilterModel;
        this.objectMapper = objectMapper;
        this.syntheticSelectedCompetitorFactory = syntheticSelectedCompetitorFactory;
    }

    public List<SelectedCompetitor> filter(
            UUID projectId,
            IndustryType industry,
            String tradeAreaLabel,
            List<ExtractedPlace> places) {
        IndustryType safeIndustry = industry != null ? industry : IndustryType.OTHER;
        String area = tradeAreaLabel != null ? tradeAreaLabel.trim() : "";
        List<ExtractedPlace> safePlaces = places != null ? places : List.of();
        if (safePlaces.isEmpty()) {
            return List.of(
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 0),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 1),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 2));
        }
        UUID reservationId = creditVaultService.reserve(projectId, COMPETITOR_FILTER_CREDIT);
        try {
            String rawJson = geminiCompetitorFilterModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from(CompetitorFilterPrompts.systemMessage()),
                            UserMessage.from(CompetitorFilterPrompts.userMessage(safeIndustry, area, safePlaces)))
                    .build()).aiMessage().text();
            List<CompetitorFilterAiSelection> selections = parseSelections(rawJson);
            List<SelectedCompetitor> merged = mergeSelections(selections, safePlaces);
            padSyntheticToThree(merged, safeIndustry, area);
            creditVaultService.settle(reservationId, COMPETITOR_FILTER_CREDIT, "competitor_filter");
            return List.copyOf(merged);
        } catch (Throwable throwable) {
            creditVaultService.refund(reservationId);
            return List.of(
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 0),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 1),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 2));
        }
    }

    public List<SelectedCompetitor> filterFromSerpOrganic(
            UUID projectId,
            IndustryType industry,
            String contextLabel,
            String targetBrand,
            String targetUrl,
            List<SerpOrganicResult> organics,
            CompetitorExtractionMode promptProfile) {
        IndustryType safeIndustry = industry != null ? industry : IndustryType.OTHER;
        String area = contextLabel != null ? contextLabel.trim() : "";
        List<SerpOrganicResult> safeOrganics = organics != null ? organics : List.of();
        if (safeOrganics.isEmpty()) {
            return List.of(
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 0),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 1),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 2));
        }
        String systemMsg =
                promptProfile == CompetitorExtractionMode.ONLINE_SERVICE
                        ? CompetitorFilterPrompts.systemMessageOnlineService()
                        : CompetitorFilterPrompts.systemMessageCorporateService();
        List<ExtractedPlace> places = new ArrayList<>();
        for (SerpOrganicResult o : safeOrganics) {
            places.add(new ExtractedPlace(o.title(), o.link(), null, null));
        }
        UUID reservationId = creditVaultService.reserve(projectId, COMPETITOR_FILTER_CREDIT);
        try {
            String rawJson = geminiCompetitorFilterModel.chat(ChatRequest.builder()
                    .messages(
                            SystemMessage.from(systemMsg),
                            UserMessage.from(CompetitorFilterPrompts.userMessageSerp(
                                    safeIndustry, area, targetBrand, targetUrl, safeOrganics)))
                    .build()).aiMessage().text();
            List<CompetitorFilterAiSelection> selections = parseSelections(rawJson);
            List<SelectedCompetitor> merged = mergeSelections(selections, places);
            padSyntheticToThree(merged, safeIndustry, area);
            creditVaultService.settle(reservationId, COMPETITOR_FILTER_CREDIT, "competitor_filter_serp");
            return List.copyOf(merged);
        } catch (Throwable throwable) {
            creditVaultService.refund(reservationId);
            return List.of(
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 0),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 1),
                    syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(safeIndustry, area, 2));
        }
    }

    private List<CompetitorFilterAiSelection> parseSelections(String rawJson) throws Exception {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode selectionsNode = root.path("selections");
        List<CompetitorFilterAiSelection> list = new ArrayList<>();
        if (!selectionsNode.isArray()) {
            return list;
        }
        for (JsonNode node : selectionsNode) {
            Integer sourceIndex = null;
            if (node.has("sourceIndex") && node.get("sourceIndex").isIntegralNumber()) {
                sourceIndex = node.get("sourceIndex").intValue();
            }
            String reasoning = "";
            JsonNode reasoningNode = node.get("reasoning");
            if (reasoningNode != null && !reasoningNode.isNull()) {
                reasoning = reasoningNode.asText();
            }
            list.add(new CompetitorFilterAiSelection(sourceIndex, reasoning));
        }
        return list;
    }

    private static List<SelectedCompetitor> mergeSelections(
            List<CompetitorFilterAiSelection> selections,
            List<ExtractedPlace> places) {
        List<SelectedCompetitor> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        if (selections != null) {
            for (CompetitorFilterAiSelection selection : selections) {
                if (result.size() >= 3) {
                    break;
                }
                if (selection == null || selection.sourceIndex() == null) {
                    continue;
                }
                int idx = selection.sourceIndex();
                if (idx < 0 || idx >= places.size()) {
                    continue;
                }
                if (!used.add(idx)) {
                    continue;
                }
                ExtractedPlace place = places.get(idx);
                String reasoning = selection.reasoning() != null ? selection.reasoning() : "";
                result.add(new SelectedCompetitor(
                        place.name(),
                        place.websiteUrl(),
                        place.rating(),
                        place.userRatingsTotal(),
                        reasoning,
                        false));
            }
        }
        return result;
    }

    private void padSyntheticToThree(List<SelectedCompetitor> merged, IndustryType industry, String tradeAreaLabel) {
        int ordinal = 0;
        while (merged.size() < 3) {
            merged.add(syntheticSelectedCompetitorFactory.singleFilterPadPlaceholder(industry, tradeAreaLabel, ordinal));
            ordinal++;
        }
    }
}
