package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.GeoOnboardingLlmResult;
import com.geo.analytics.infrastructure.config.AiConfig;
import com.geo.analytics.domain.ai.CitationValidator;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.ai.DebatePersonaSystemPrompts;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.logic.CalibrationCalculator;
import com.geo.analytics.domain.logic.ConvergenceController;
import com.geo.analytics.domain.logic.DebateTextMathHeuristics;
import com.geo.analytics.domain.logic.InformationGainCalculator;
import com.geo.analytics.application.security.PromptInjectionValidator;
import com.geo.analytics.domain.logic.SeoDataEvidenceProvider;
import com.geo.analytics.domain.logic.SeoEvidenceXmlBuilder;
import com.geo.analytics.domain.logic.TokenProfitGuard;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.domain.model.SeoEvidence;
import com.geo.analytics.domain.model.SeoOrganicRow;
import com.geo.analytics.infrastructure.ai.DirectorOnboardingJson;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DebateOnboardingOrchestrator {
    private static final int MAX_DEBATE_TURNS = 5;
    private static final double CONVERGENCE_FRICTION = 0.5d;
    private static final boolean[] IS_INNOVATOR = new boolean[] {false, true, false};
    private static final int RAG_EVIDENCE_MAX_COUNT = 5;
    private static final String DEFAULT_EVIDENCE_RELEVANCE_LABEL = "OTHER";

    private static final String REJECTED_INNOVATOR =
            "（イノベーター出力は引用形式が不十分なためこのラウンドでは採用しません。）";
    private static final Logger log = LoggerFactory.getLogger(DebateOnboardingOrchestrator.class);
    private final ChatLanguageModel analystChatModel;
    private final ChatLanguageModel innovatorChatModel;
    private final ChatLanguageModel skepticChatModel;
    private final ChatLanguageModel directorChatModel;
    private final ObjectMapper objectMapper;
    private final SeoDataEvidenceProvider seoDataEvidenceProvider;
    private final TokenProfitBudgetResolver tokenProfitBudgetResolver;
    private final PromptInjectionValidator promptInjectionValidator;
    private final DebateMathAuditService debateMathAuditService;

    public DebateOnboardingOrchestrator(
            @Qualifier(AiConfig.GEMINI_DEBATE_ANALYST) ChatLanguageModel analystChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_INNOVATOR) ChatLanguageModel innovatorChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_SKEPTIC) ChatLanguageModel skepticChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_DIRECTOR) ChatLanguageModel directorChatModel,
            ObjectMapper objectMapper,
            SeoDataEvidenceProvider seoDataEvidenceProvider,
            TokenProfitBudgetResolver tokenProfitBudgetResolver,
            PromptInjectionValidator promptInjectionValidator,
            DebateMathAuditService debateMathAuditService) {
        this.analystChatModel = analystChatModel;
        this.innovatorChatModel = innovatorChatModel;
        this.skepticChatModel = skepticChatModel;
        this.directorChatModel = directorChatModel;
        this.objectMapper = objectMapper;
        this.seoDataEvidenceProvider = Objects.requireNonNull(seoDataEvidenceProvider, "seoDataEvidenceProvider");
        this.tokenProfitBudgetResolver =
                Objects.requireNonNull(tokenProfitBudgetResolver, "tokenProfitBudgetResolver");
        this.promptInjectionValidator =
                Objects.requireNonNull(promptInjectionValidator, "promptInjectionValidator");
        this.debateMathAuditService = Objects.requireNonNull(debateMathAuditService, "debateMathAuditService");
    }

    public GeoOnboardingLlmResult runDebateOnboarding(
            String plainText,
            UUID targetId,
            String searchQuery,
            List<SeoOrganicRow> rawSeoRows,
            IndustryType industryHint) {
        Objects.requireNonNull(targetId, "targetId");
        IndustryType resolvedIndustry = industryHint != null ? industryHint : IndustryType.OTHER;
        String q = searchQuery == null ? "" : searchQuery;
        String wrapped = "<scraped_data>\n" + plainText + "\n</scraped_data>";
        List<SeoEvidence> seoEvidences = List.of();
        if (rawSeoRows != null && !rawSeoRows.isEmpty()) {
            seoEvidences =
                    seoDataEvidenceProvider.provideEvidences(
                            q,
                            rawSeoRows,
                            RAG_EVIDENCE_MAX_COUNT,
                            SeoDataEvidenceProvider.DEFAULT_MAX_PER_DOMAIN,
                            DEFAULT_EVIDENCE_RELEVANCE_LABEL);
            SubscriptionPlan subscriptionPlan =
                    TenantPlanScope.currentSubscriptionPlan().orElse(SubscriptionPlan.STANDARD);
            int maxCompetitorXmlChars = tokenProfitBudgetResolver.maxCompetitorXmlChars(subscriptionPlan);
            seoEvidences = TokenProfitGuard.clipEvidences(seoEvidences, maxCompetitorXmlChars);
        }
        String competitorXml =
                (seoEvidences == null || seoEvidences.isEmpty())
                        ? ""
                        : SeoEvidenceXmlBuilder.buildCompetitorBlock(seoEvidences);
        boolean competitorXmlIncluded = false;
        List<SeoEvidence> usedEvidencesForAudit = List.of();
        if (!competitorXml.isEmpty()) {
            if (promptInjectionValidator.isCompetitorXmlSafe(competitorXml)) {
                competitorXmlIncluded = true;
                usedEvidencesForAudit = List.copyOf(seoEvidences);
            } else {
                log.warn("competitor XML rejected by prompt-injection guard; continuing without competitor block");
                competitorXml = "";
            }
        }
        String contextAnalystBase =
                competitorXml.isEmpty() ? wrapped : wrapped + "\n\n" + competitorXml;
        String contextInnovatorBase = wrapped;
        StringBuilder debateAccumulator = new StringBuilder();
        double[] prevConfidences = null;
        double[] prevCentroid = null;
        String analystText = "";
        String innovatorRaw = "";
        String innovatorForHistory = "";
        String skepticText = "";
        String stopReason = "MAX_TURNS_REACHED";
        int completedRounds = 0;
        ConvergenceController.ConvergenceSnapshot lastSnapshot = null;
        double lastGeoIg = 0.0d;
        double lastTrust = 0.0d;

        for (int turn = 0; turn < MAX_DEBATE_TURNS; turn++) {
            String userForAnalyst = contextAnalystBase;
            String userForInnovator = contextInnovatorBase;
            if (debateAccumulator.length() > 0) {
                String accumulated =
                        "\n\n## これまでの議論の蓄積\n" + debateAccumulator;
                userForAnalyst = contextAnalystBase + accumulated;
                userForInnovator = contextInnovatorBase + accumulated;
            }
            analystText =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.ANALYST, resolvedIndustry),
                            userForAnalyst,
                            analystChatModel);
            innovatorRaw =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.INNOVATOR, resolvedIndustry),
                            userForInnovator,
                            innovatorChatModel);
            innovatorForHistory = innovatorRaw;
            if (!CitationValidator.hasValidCitation(innovatorRaw)) {
                log.warn("innovator output rejected: insufficient citation format");
                innovatorForHistory = REJECTED_INNOVATOR;
            }
            String skepticInput = buildSkepticUserMessage(analystText, innovatorForHistory, debateAccumulator);
            skepticText =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.SKEPTIC, resolvedIndustry),
                            skepticInput,
                            skepticChatModel);

            debateAccumulator
                    .append("\n## ラウンド ")
                    .append(turn + 1)
                    .append("\n### アナリスト\n")
                    .append(analystText)
                    .append("\n### イノベーター（履歴用）\n")
                    .append(innovatorForHistory)
                    .append("\n### スケプティック\n")
                    .append(skepticText);

            DebateTextMathHeuristics.RoundHeuristicResult math =
                    DebateTextMathHeuristics.compute(plainText, analystText, innovatorForHistory, skepticText);
            lastGeoIg =
                    InformationGainCalculator.geoInformationGain(
                            math.qIntent(),
                            math.sDensity(),
                            math.pSite(),
                            DebateTextMathHeuristics.P_MARKET_UNIFORM_4);
            lastTrust = CalibrationCalculator.calibratedBelief(math.agentMass(), IS_INNOVATOR, CONVERGENCE_FRICTION, lastGeoIg);

            double[] currConfidences = math.currConfidences();
            double[] currCentroid = math.currCentroid();
            if (turn > 0 && prevConfidences != null && prevCentroid != null) {
                lastSnapshot =
                        ConvergenceController.computeConvergenceSnapshot(
                                prevConfidences,
                                currConfidences,
                                prevCentroid,
                                currCentroid,
                                CONVERGENCE_FRICTION,
                                turn);
                if (ConvergenceController.shouldStop(
                        prevConfidences,
                        currConfidences,
                        prevCentroid,
                        currCentroid,
                        CONVERGENCE_FRICTION,
                        turn)) {
                    stopReason = "CONVERGED";
                    completedRounds = turn + 1;
                    break;
                }
            }
            prevConfidences = Arrays.copyOf(currConfidences, currConfidences.length);
            prevCentroid = Arrays.copyOf(currCentroid, currCentroid.length);
            completedRounds = turn + 1;
        }

        String directorInput =
                "以下は全ラウンドの記録です。指定のJSON形式に厳密に従い最終出力せよ。\n"
                        + "【本パイプで算出した数理指標（JSON以外の人間可読要約）】\n"
                        + "情報利得(GEO-IG)スカラーの最終採用値: "
                        + lastGeoIg
                        + " / 較正後信頼度スカラー: "
                        + lastTrust
                        + "\n\n## 原文\n"
                        + contextAnalystBase
                        + "\n\n## アナリスト（最終ラウンド抜粋）\n"
                        + analystText
                        + "\n\n## イノベーター（生テキスト、最終ラウンド）\n"
                        + innovatorRaw
                        + "\n\n## スケプティック（最終ラウンド）\n"
                        + skepticText
                        + "\n\n## 蓄積された議論ログ\n"
                        + debateAccumulator;
        String directorSystem =
                DebatePersonaSystemPrompts.forDirectorWithScoreInjection(
                        lastGeoIg, lastTrust, resolvedIndustry);
        String rawJson = singleChat(directorSystem, directorInput, directorChatModel);

        debateMathAuditService.saveOnboardingAudit(
                targetId,
                completedRounds,
                stopReason,
                lastSnapshot,
                lastGeoIg,
                lastTrust,
                CONVERGENCE_FRICTION,
                competitorXmlIncluded,
                usedEvidencesForAudit);

        return mapDirectorToResult(rawJson);
    }

    private static String buildSkepticUserMessage(
            String analystText, String innovatorForHistory, StringBuilder priorDebateAccumulator) {
        StringBuilder base =
                new StringBuilder(512)
                        .append("以下は同一サイト由来の分析です。\n\n## アナリストの事実整理\n")
                        .append(analystText)
                        .append("\n\n## イノベーターの提案\n")
                        .append(innovatorForHistory)
                        .append("\n\n## あなたの任務\n上記に対しスケプティックに批判を述べてください。");
        if (priorDebateAccumulator.length() > 0) {
            base.append("\n\n## これまでのラウンドの議論（参考）\n").append(priorDebateAccumulator);
        }
        return base.toString();
    }

    private String singleChat(String systemPrompt, String userContent, ChatLanguageModel chatLanguageModel) {
        try {
            return chatLanguageModel
                    .chat(
                            ChatRequest.builder()
                                    .messages(SystemMessage.from(systemPrompt), UserMessage.from(userContent))
                                    .build())
                    .aiMessage()
                    .text();
        } catch (RestClientResponseException restClientResponseException) {
            log.error(
                    "debate chat Gemini status={} body={}",
                    restClientResponseException.getStatusCode(),
                    restClientResponseException.getResponseBodyAsString(),
                    restClientResponseException);
            throw new RuntimeException(restClientResponseException);
        }
    }

    private GeoOnboardingLlmResult mapDirectorToResult(String rawJson) {
        DirectorOnboardingJson doc;
        try {
            doc = objectMapper.readValue(rawJson, DirectorOnboardingJson.class);
        } catch (JsonProcessingException jsonProcessingException) {
            log.error("debate director parse failed raw={}", rawJson, jsonProcessingException);
            throw new RuntimeException("parse", jsonProcessingException);
        }
        IndustryType industry;
        try {
            String ind = doc.industryType() == null ? "OTHER" : doc.industryType().trim();
            industry = IndustryType.valueOf(ind);
        } catch (IllegalArgumentException illegalArgumentException) {
            industry = IndustryType.OTHER;
        }
        String es = doc.extractedStrengths() == null ? "" : doc.extractedStrengths();
        List<String> strengths = new ArrayList<>();
        for (String line : es.split("\n", -1)) {
            if (!line.isEmpty()) {
                strengths.add(line);
            }
        }
        String targetAudience = doc.targetAudience() == null ? "" : doc.targetAudience();
        List<MinorityReport> minorityReports;
        if (doc.minorityReports() == null || doc.minorityReports().isEmpty()) {
            minorityReports = List.of();
        } else {
            minorityReports =
                    doc.minorityReports().stream()
                            .map(
                                    item ->
                                            new MinorityReport(
                                                    item == null || item.insight() == null ? "" : item.insight(),
                                                    item == null || item.conflictReason() == null
                                                            ? ""
                                                            : item.conflictReason(),
                                                    item == null || item.evidence() == null
                                                            ? ""
                                                            : item.evidence()))
                            .toList();
        }
        return new GeoOnboardingLlmResult(industry, strengths, targetAudience, minorityReports);
    }
}
