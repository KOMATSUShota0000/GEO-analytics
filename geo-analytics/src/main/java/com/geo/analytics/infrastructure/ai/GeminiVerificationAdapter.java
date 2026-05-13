package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.application.dto.CompetitorResult;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.ModelTypedAiVerificationPort;
import com.geo.analytics.domain.enums.MatchStatus;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.domain.service.SomScoreCalculator;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClientResponseException;
import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class GeminiVerificationAdapter implements ModelTypedAiVerificationPort {
    private static final Logger log = LoggerFactory.getLogger(GeminiVerificationAdapter.class);
    private final ChatLanguageModel geminiGbvsChatModel;
    private final SomScoreParser somScoreParser;
    private final EntityNormalizer entityNormalizer;
    private final JapaneseNlpService japaneseNlpService;
    private final GeoVisibilityCalculatorService geoVisibilityCalculatorService;
    private final JobPersistenceService jobPersistenceService;

    public GeminiVerificationAdapter(
            @Qualifier(AiConfig.GEMINI_GBVS_CHAT) ChatLanguageModel geminiGbvsChatModel,
            SomScoreParser somScoreParser,
            EntityNormalizer entityNormalizer,
            JapaneseNlpService japaneseNlpService,
            GeoVisibilityCalculatorService geoVisibilityCalculatorService,
            JobPersistenceService jobPersistenceService) {
        this.geminiGbvsChatModel = geminiGbvsChatModel;
        this.somScoreParser = somScoreParser;
        this.entityNormalizer = entityNormalizer;
        this.japaneseNlpService = japaneseNlpService;
        this.geoVisibilityCalculatorService = geoVisibilityCalculatorService;
        this.jobPersistenceService = jobPersistenceService;
    }

    @Override
    public ModelType modelType() {
        return ModelType.GEMINI;
    }

    @Override
    public VerificationResponse verify(VerificationRequest verificationRequest) {
        SubscriptionPlan plan = verificationRequest.subscriptionPlan();
        return verifyWithoutJobStream(verificationRequest, plan);
    }

    private record PreparedHandoff(String userMessage) {}

    private PreparedHandoff prepareHandoff(VerificationRequest verificationRequest) {
        String userBody;
        var crawled = verificationRequest.crawledContent();
        var clippedCrawl = crawled != null ? LlmWebsiteTextClip.clipWebsiteText(crawled) : null;
        if (clippedCrawl == null || clippedCrawl.isBlank()) {
            log.warn(
                    "Crawl data is empty/blank. Falling back to internal knowledge mode. brand=\"{}\" query=\"{}\"",
                    verificationRequest.brandName(),
                    verificationRequest.query());
            userBody = ConsultantPrompts.userTextBrandQueryOnly(
                    verificationRequest.brandName(), verificationRequest.query());
        } else {
            double trust = verificationRequest.domainTrustScore() != null ? verificationRequest.domainTrustScore() : 1.0;
            userBody = ConsultantPrompts.userTextBrandQueryWithWebsiteExtract(
                    verificationRequest.brandName(),
                    verificationRequest.query(),
                    clippedCrawl,
                    trust);
        }
        return new PreparedHandoff(applyJobContextPrefix(verificationRequest, userBody));
    }

    private String applyJobContextPrefix(VerificationRequest verificationRequest, String userMessage) {
        UUID jobId = verificationRequest.jobId();
        if (jobId == null) {
            return userMessage;
        }
        return jobPersistenceService
                .findJobByIdOptional(jobId)
                .map(job -> JobPromptContextFormatter.format(job) + "\n\n" + userMessage)
                .orElse(userMessage);
    }

    private static String evaluatedBrandLabel(VerificationRequest verificationRequest) {
        String main = verificationRequest.canonicalMainBrand();
        if (main != null && !main.isBlank()) {
            return main.trim();
        }
        String brand = verificationRequest.brandName();
        return brand != null ? brand : "";
    }

    private List<ChatMessage> chatMessagesForPlan(
            SubscriptionPlan plan,
            PreparedHandoff handoff,
            VerificationRequest verificationRequest) {
        String brandLabel = evaluatedBrandLabel(verificationRequest);
        String system = ConsultantPrompts.systemText(plan, brandLabel);
        return List.of(SystemMessage.from(system), UserMessage.from(handoff.userMessage()));
    }

    private static String formatWiretapPrompt(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "(no messages)";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if (msg instanceof SystemMessage systemMessage) {
                sb.append("=== SYSTEM ===\n").append(systemMessage.text());
            } else if (msg instanceof UserMessage userMessage) {
                sb.append("=== USER ===\n").append(userMessage.text());
            } else {
                sb.append("=== ").append(msg.type()).append(" ===\n").append(msg);
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private void logGeminiPrompt(VerificationRequest verificationRequest, SubscriptionPlan plan, List<ChatMessage> chatMessages) {
        log.info(
                "[GEMINI PROMPT] jobId={} queryId={} plan={} structuredOutputSchema={}\n{}",
                verificationRequest.jobId(),
                verificationRequest.queryId(),
                plan,
                ConsultantOutputSchema.class.getSimpleName(),
                formatWiretapPrompt(chatMessages));
    }

    private void logGeminiResponse(VerificationRequest verificationRequest, String rawText) {
        log.info(
                "[GEMINI RESPONSE] jobId={} queryId={}\n{}",
                verificationRequest.jobId(),
                verificationRequest.queryId(),
                rawText != null ? rawText : "");
    }

    private VerificationResponse verifyWithoutJobStream(VerificationRequest verificationRequest, SubscriptionPlan plan) {
        try {
            var handoff = prepareHandoff(verificationRequest);
            List<ChatMessage> messages = chatMessagesForPlan(plan, handoff, verificationRequest);
            logGeminiPrompt(verificationRequest, plan, messages);
            String rawAiResponseJson = geminiGbvsChatModel.chat(ChatRequest.builder()
                .messages(messages)
                .responseFormat(ConsultantOutputSchema.responseFormat(plan))
                .build()).aiMessage().text();
            logGeminiResponse(verificationRequest, rawAiResponseJson);
            return buildVerificationResponse(rawAiResponseJson, plan, verificationRequest);
        } catch (Exception exception) {
            log.error("Gemini verification failed rawDetail={}", formatGeminiErrorDetail(exception), exception);
            throw exception;
        }
    }

    private VerificationResponse buildVerificationResponse(
            String rawAiResponseJson,
            SubscriptionPlan subscriptionPlan,
            VerificationRequest verificationRequest) {
        SomScoreData metrics = somScoreParser.parse(rawAiResponseJson);
        ConsultantOutputData full = somScoreParser.parseConsultantOutput(rawAiResponseJson);
        int tc = metrics.tokenCount() != null ? metrics.tokenCount() : 0;
        Integer rp = metrics.aiCitationPosition();
        double si = metrics.sentimentIntensity() != null ? metrics.sentimentIntensity() : 0.0;
        String ext = full.extractedBrandMention();
        String rawName = ext != null && !ext.isBlank() ? ext : verificationRequest.brandName();
        String main = verificationRequest.canonicalMainBrand() != null
            && !verificationRequest.canonicalMainBrand().isBlank()
            ? verificationRequest.canonicalMainBrand()
            : verificationRequest.brandName();
        List<String> comps = verificationRequest.registeredCompetitorBrands() != null
            ? verificationRequest.registeredCompetitorBrands()
            : List.of();
        boolean isProPlan = subscriptionPlan.usesProTierFeatures();
        String nlpSource = full.response() != null ? full.response().strip() : "";
        si = StrictMath.max(-1.0, StrictMath.min(1.0, si));
        var responseTokens = geoVisibilityCalculatorService.tokenizeResponseForMentions(nlpSource);
        List<String> needles = GeoVisibilityCalculatorService.splitBrandAliasPhrases(main, rawName);
        int nounCount = geoVisibilityCalculatorService.countNormalizedMentions(responseTokens, needles);
        int responseTokenLength = japaneseNlpService.totalTokenCount(nlpSource);
        double stuffingDensity = 0.0;
        String resolved = entityNormalizer.resolve(rawName, main, comps, isProPlan);
        double sourceWeight = GeoVisibilityCalculatorService.sourceWeightFromUrl(verificationRequest.url());
        SomRawMetrics rawMetrics = metrics.toRawMetrics(
                subscriptionPlan, si, responseTokenLength, nounCount, stuffingDensity, sourceWeight);
        var lAvgSingle = responseTokenLength > 0 ? (double) responseTokenLength : 0.0;
        GeoVisibilityCalculatorService.GbvsResult gbvs;
        if (verificationRequest.jobId() != null) {
            long pq = jobPersistenceService.countQueriesByJobId(verificationRequest.jobId());
            gbvs = SomScoreCalculator.computeWithPlannedQueries(rawMetrics, lAvgSingle, pq);
        } else {
            gbvs = SomScoreCalculator.compute(rawMetrics, lAvgSingle);
        }
        double gbvsNormalizedScore = gbvs.scorePercent();
        var som = StrictMath.max(0.0, StrictMath.min(100.0, gbvsNormalizedScore));
        boolean brand = Boolean.TRUE.equals(full.brandMentioned());
        int overall = (int) StrictMath.round(StrictMath.max(0.0, StrictMath.min(100.0, som)));
        var compList = new ArrayList<CompetitorResult>();
        if (full.competitorComparison() != null) {
            var idx = 0;
            for (var entry : full.competitorComparison()) {
                var label = entry.competitorName() != null ? entry.competitorName() : "";
                List<String> compNeedles = GeoVisibilityCalculatorService.splitBrandAliasPhrases(label, label);
                int compNounCount = geoVisibilityCalculatorService.countNormalizedMentions(responseTokens, compNeedles);
                var shareSom = entry.share() != null ? entry.share() * 100.0 : 0.0;
                var vs = gbvs.visibilityStage();
                compList.add(new CompetitorResult(
                        label,
                        shareSom,
                        idx + 1,
                        vs,
                        MatchStatus.AUTO_MATCH,
                        compNounCount));
                idx++;
            }
        }
        return new VerificationResponse(
                ModelType.GEMINI,
                rawAiResponseJson,
                som,
                brand,
                rp,
                overall,
                tc,
                rp,
                si,
                resolved,
                gbvs.visibilityStage(),
                gbvs.modifiedZScore(),
                GeoVisibilityCalculatorService.CALCULATION_VERSION,
                compList,
                new LinkedHashMap<>(),
                gbvsNormalizedScore);
    }

    private static String formatGeminiErrorDetail(Throwable throwable) {
        StringBuilder stringBuilder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 12) {
            if (depth > 0) {
                stringBuilder.append(" | cause: ");
            }
            stringBuilder.append(current.getClass().getName()).append(": ").append(current.getMessage());
            if (current instanceof RestClientResponseException restClientResponseException) {
                String body = restClientResponseException.getResponseBodyAsString();
                if (body != null && !body.isBlank()) {
                    stringBuilder.append(" body=").append(body);
                }
            }
            current = current.getCause();
            depth++;
        }
        return stringBuilder.toString();
    }
}
