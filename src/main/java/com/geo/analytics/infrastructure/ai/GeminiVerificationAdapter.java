package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.application.dto.CompetitorResult;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.ModelTypedAiVerificationPort;
import com.geo.analytics.domain.enums.ModelType;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.BrandMentionEngine;
import com.geo.analytics.domain.service.CompetitorSelection;
import com.geo.analytics.domain.model.BrandMentionMetrics;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
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
    private final BrandMentionEngine brandMentionEngine;
    private final JobPersistenceService jobPersistenceService;

    public GeminiVerificationAdapter(
            @Qualifier(AiConfig.GEMINI_GBVS_CHAT) ChatLanguageModel geminiGbvsChatModel,
            SomScoreParser somScoreParser,
            EntityNormalizer entityNormalizer,
            BrandMentionEngine brandMentionEngine,
            JobPersistenceService jobPersistenceService) {
        this.geminiGbvsChatModel = geminiGbvsChatModel;
        this.somScoreParser = somScoreParser;
        this.entityNormalizer = entityNormalizer;
        this.brandMentionEngine = brandMentionEngine;
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
        var aiOverviewText = verificationRequest.aiOverviewText();
        if (aiOverviewText == null || aiOverviewText.isBlank()) {
            log.info(
                    "verification_material=estimated brand=\"{}\" query=\"{}\" jobId={} queryId={}",
                    verificationRequest.brandName(),
                    verificationRequest.query(),
                    verificationRequest.jobId(),
                    verificationRequest.queryId());
        }
        return new PreparedHandoff(ConsultantPrompts.userBody(
                verificationRequest.brandName(),
                verificationRequest.query(),
                aiOverviewText,
                resolveJobPromptContext(verificationRequest)));
    }

    private String resolveJobPromptContext(VerificationRequest verificationRequest) {
        UUID jobId = verificationRequest.jobId();
        if (jobId == null) {
            return null;
        }
        return jobPersistenceService
                .findJobByIdOptional(jobId)
                .map(JobPromptContextFormatter::format)
                .orElse(null);
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

        double si = metrics.sentimentIntensity() != null ? metrics.sentimentIntensity() : 0.0;
        String ext = full.extractedBrandMention();
        String rawName = ext != null && !ext.isBlank() ? ext : verificationRequest.brandName();
        String main = verificationRequest.canonicalMainBrand() != null
            && !verificationRequest.canonicalMainBrand().isBlank()
            ? verificationRequest.canonicalMainBrand()
            : verificationRequest.brandName();
        boolean isProPlan = subscriptionPlan.usesProTierFeatures();
        String nlpSource = full.response() != null ? full.response().strip() : "";
        // Why: 言及回数・言及文字数・トークン数はすべて Java の実測値にする（#59 / #60）。旧実装は LLM 申告の
        //      「文字数」を回数として渡しており、計算式が単位の合わない割り算になっていた。
        BrandMentionMetrics measuredMention = brandMentionEngine.measure(nlpSource, main);
        int tc = measuredMention.mentionChars();
        int responseTokenLength = measuredMention.totalTokens();
        String resolved = entityNormalizer.resolve(rawName, main);
        // Why: 引用順位は回答文から Java で決める（#66 / ADR-047）。LLM 申告は比較のためログにだけ残す。
        int measuredCitationPosition =
                brandMentionEngine.citationPosition(nlpSource, main, namedBrandsOf(full));
        SomScoreData measuredMetrics = metrics.withAiCitationPosition(
                measuredCitationPosition > 0 ? measuredCitationPosition : null);
        Integer rp = measuredMetrics.aiCitationPosition();
        log.info(
                "citation_position jobId={} queryId={} java={} llm={}",
                verificationRequest.jobId(),
                verificationRequest.queryId(),
                measuredCitationPosition,
                metrics.aiCitationPosition());
        log.info(
                "mention_metrics jobId={} queryId={} count={} chars={} tokens={} llmTokenCount={}",
                verificationRequest.jobId(),
                verificationRequest.queryId(),
                measuredMention.mentionCount(),
                measuredMention.mentionChars(),
                measuredMention.totalTokens(),
                metrics.tokenCount());
        SomRawMetrics rawMetrics = measuredMetrics.toRawMetrics(
                subscriptionPlan, si, responseTokenLength, measuredMention.mentionCount(),
                measuredMention.mentionChars());
        var lAvgSingle = responseTokenLength > 0 ? (double) responseTokenLength : 0.0;
        GeoVisibilityCalculatorService.GbvsResult gbvs = SomScoreCalculator.compute(rawMetrics, lAvgSingle);
        double gbvsNormalizedScore = gbvs.scorePercent();
        var som = StrictMath.max(0.0, StrictMath.min(100.0, gbvsNormalizedScore));
        boolean brand = Boolean.TRUE.equals(full.brandMentioned());
        int overall = (int) StrictMath.round(StrictMath.max(0.0, StrictMath.min(100.0, som)));
        // Why: 競合は「回答文に同時に登場した他ブランド」。名前は LLM が挙げ、数と順序は Java が本文から測る
        //      （#64 / .cursorrules 12節）。自社・残余カテゴリ・表記ゆれの重複はここで落とす（#65）。
        // Why: 競合は「回答文に同時に登場した他ブランド」。名前は LLM が挙げ、選別と計数は Java が行う
        //      （#64 / #65 / .cursorrules 12節）。自社・残余カテゴリ・表記ゆれの重複は選別で落とす。
        var compList = new ArrayList<CompetitorResult>();
        var acceptedLabels = CompetitorSelection.accept(namedBrandsOf(full), main, entityNormalizer);
        if (!acceptedLabels.isEmpty()) {
            var rankingNames = new ArrayList<String>(acceptedLabels.size() + 1);
            rankingNames.add(main);
            rankingNames.addAll(acceptedLabels);
            for (String label : acceptedLabels) {
                BrandMentionMetrics competitorMention = brandMentionEngine.measure(nlpSource, label);
                int competitorPosition = brandMentionEngine.citationPosition(nlpSource, label, rankingNames);
                SomRawMetrics competitorMetrics = new SomRawMetrics(
                        competitorMention.mentionChars(),
                        competitorPosition > 0 ? competitorPosition : null,
                        0.0,
                        isProPlan,
                        competitorMention.mentionCount() > 0,
                        competitorMention.mentionCount(),
                        competitorMention.totalTokens());
                double competitorSom =
                        SomScoreCalculator.compute(competitorMetrics, lAvgSingle).scorePercent();
                compList.add(new CompetitorResult(
                        label,
                        competitorSom,
                        competitorPosition > 0 ? competitorPosition : null,
                        competitorMention.mentionCount()));
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
                GeoVisibilityCalculatorService.CALCULATION_VERSION_AIOVERVIEW,
                compList,
                new LinkedHashMap<>(),
                gbvsNormalizedScore);
    }

    /** 引用順位の比較対象。回答文に実際に名前が出たブランドを LLM が挙げたもの（名前は LLM、順序は Java）。 */
    private static List<String> namedBrandsOf(ConsultantOutputData consultantOutputData) {
        if (consultantOutputData.competitorComparison() == null) {
            return List.of();
        }
        return consultantOutputData.competitorComparison().stream()
                .map(entry -> entry.competitorName())
                .filter(name -> name != null && !name.isBlank())
                .toList();
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
