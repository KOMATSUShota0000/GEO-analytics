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
import com.geo.analytics.application.service.JobStreamRegistryService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.domain.service.SomScoreCalculator;
import com.geo.analytics.infrastructure.config.AiConfig;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClientResponseException;
import java.lang.StrictMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class GeminiVerificationAdapter implements ModelTypedAiVerificationPort {
    private static final Logger log = LoggerFactory.getLogger(GeminiVerificationAdapter.class);
    private final ChatLanguageModel geminiGbvsChatModel;
    private final SomScoreParser somScoreParser;
    private final JobStreamRegistryService jobStreamRegistryService;
    private final GoogleAiGeminiStreamingChatModel geminiStreamingStandard;
    private final GoogleAiGeminiStreamingChatModel geminiStreamingPro;
    private final EntityNormalizer entityNormalizer;
    private final JapaneseNlpService japaneseNlpService;
    private final DeepSeekAdapter deepSeekAdapter;
    private final StrictSchemaValidator strictSchemaValidator;
    private final GeoVisibilityCalculatorService geoVisibilityCalculatorService;
    private final JobPersistenceService jobPersistenceService;

    public GeminiVerificationAdapter(
            @Qualifier(AiConfig.GEMINI_GBVS_CHAT) ChatLanguageModel geminiGbvsChatModel,
            SomScoreParser somScoreParser,
            JobStreamRegistryService jobStreamRegistryService,
            @Qualifier(AiConfig.GEMINI_STREAMING_STANDARD) GoogleAiGeminiStreamingChatModel geminiStreamingStandard,
            @Qualifier(AiConfig.GEMINI_STREAMING_PRO) GoogleAiGeminiStreamingChatModel geminiStreamingPro,
            EntityNormalizer entityNormalizer,
            JapaneseNlpService japaneseNlpService,
            DeepSeekAdapter deepSeekAdapter,
            StrictSchemaValidator strictSchemaValidator,
            GeoVisibilityCalculatorService geoVisibilityCalculatorService,
            JobPersistenceService jobPersistenceService) {
        this.geminiGbvsChatModel = geminiGbvsChatModel;
        this.somScoreParser = somScoreParser;
        this.jobStreamRegistryService = jobStreamRegistryService;
        this.geminiStreamingStandard = geminiStreamingStandard;
        this.geminiStreamingPro = geminiStreamingPro;
        this.entityNormalizer = entityNormalizer;
        this.japaneseNlpService = japaneseNlpService;
        this.deepSeekAdapter = deepSeekAdapter;
        this.strictSchemaValidator = strictSchemaValidator;
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
        UUID jobId = verificationRequest.jobId();
        if (jobId == null) {
            return verifyWithoutJobStream(verificationRequest, plan);
        }
        return verifyWithJobStream(verificationRequest, plan, jobId);
    }

    private GoogleAiGeminiStreamingChatModel streamingModelForPlan(SubscriptionPlan subscriptionPlan) {
        return subscriptionPlan.usesProTierFeatures() ? geminiStreamingPro : geminiStreamingStandard;
    }

    private record PreparedHandoff(String userMessage, boolean structured) {}

    private PreparedHandoff prepareHandoff(VerificationRequest verificationRequest) {
        var crawled = verificationRequest.crawledContent();
        if (crawled == null || crawled.isBlank()) {
            return new PreparedHandoff(
                ConsultantPrompts.userTextBrandQueryOnly(verificationRequest.brandName(), verificationRequest.query()),
                false);
        }
        try {
            var rawJson = deepSeekAdapter.extractStructuredJsonBlocking(
                crawled,
                verificationRequest.url(),
                verificationRequest.brandName());
            var canonical = strictSchemaValidator.validateToCanonicalJson(rawJson);
            var trust = verificationRequest.domainTrustScore() != null ? verificationRequest.domainTrustScore() : 1.0;
            return new PreparedHandoff(
                ConsultantPrompts.userTextStructuredHandoff(
                    verificationRequest.brandName(),
                    verificationRequest.query(),
                    canonical,
                    trust),
                true);
        } catch (StructuredExtractValidationException e) {
            log.error("structured_extract_validation_failed detail={}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("deepseek_structured_pipeline_failed", e);
            throw new IllegalStateException(e);
        }
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
        var system = handoff.structured()
            ? ConsultantPrompts.systemTextGbvsStructured(plan, brandLabel)
            : ConsultantPrompts.systemText(plan, brandLabel);
        return List.of(SystemMessage.from(system), UserMessage.from(handoff.userMessage()));
    }

    private VerificationResponse verifyWithoutJobStream(VerificationRequest verificationRequest, SubscriptionPlan plan) {
        try {
            var handoff = prepareHandoff(verificationRequest);
            String rawAiResponseJson = geminiGbvsChatModel.chat(ChatRequest.builder()
                .messages(chatMessagesForPlan(plan, handoff, verificationRequest))
                .responseFormat(ConsultantOutputSchema.responseFormat(plan))
                .build()).aiMessage().text();
            return buildVerificationResponse(rawAiResponseJson, plan, verificationRequest);
        } catch (Exception exception) {
            log.error("Gemini verification failed rawDetail={}", formatGeminiErrorDetail(exception), exception);
            throw exception;
        }
    }

    private VerificationResponse verifyWithJobStream(
            VerificationRequest verificationRequest,
            SubscriptionPlan plan,
            UUID jobId) {
        UUID queryId = verificationRequest.queryId();
        CompletableFuture<VerificationResponse> verificationResponseCompletableFuture = new CompletableFuture<>();
        AtomicReference<String> aggregatedTextReference = new AtomicReference<>("");
        PreparedHandoff handoff = prepareHandoff(verificationRequest);
        try {
            GoogleAiGeminiStreamingChatModel streamingChatModel = streamingModelForPlan(plan);
            List<ChatMessage> chatMessages = chatMessagesForPlan(plan, handoff, verificationRequest);
            streamingChatModel.generate(chatMessages, new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    if (token == null || token.isEmpty()) {
                        return;
                    }
                    aggregatedTextReference.getAndUpdate(previous -> previous + token);
                    jobStreamRegistryService.sendDelta(jobId, queryId, token);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    String fullText = response.content().text() != null ? response.content().text() : aggregatedTextReference.get();
                    if (fullText == null) {
                        fullText = "";
                    }
                    jobStreamRegistryService.emitDone(jobId, queryId, fullText);
                    try {
                        verificationResponseCompletableFuture.complete(
                            buildVerificationResponse(fullText, plan, verificationRequest));
                    } catch (Exception parseException) {
                        verificationResponseCompletableFuture.completeExceptionally(parseException);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    jobStreamRegistryService.failWithError(jobId, throwable);
                    verificationResponseCompletableFuture.completeExceptionally(throwable);
                }
            });
            return verificationResponseCompletableFuture.get(600, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            log.error("Gemini streaming timed out jobId={}", jobId, timeoutException);
            throw new IllegalStateException(timeoutException);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            log.error("Gemini streaming interrupted jobId={}", jobId, interruptedException);
            throw new IllegalStateException(interruptedException);
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause() != null ? executionException.getCause() : executionException;
            log.error("Gemini streaming verification failed rawDetail={}", formatGeminiErrorDetail(cause), cause);
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private VerificationResponse buildVerificationResponse(
            String rawAiResponseJson,
            SubscriptionPlan subscriptionPlan,
            VerificationRequest verificationRequest) {
        SomScoreData metrics = somScoreParser.parse(rawAiResponseJson);
        ConsultantOutputData full = somScoreParser.parseConsultantOutput(rawAiResponseJson);
        int tc = metrics.tokenCount() != null ? metrics.tokenCount() : 0;
        int rp = metrics.rankPosition() != null ? metrics.rankPosition() : 0;
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
        String nlpSource = full.response() != null && !full.response().isBlank()
            ? full.response()
            : rawAiResponseJson;
        si = japaneseNlpService.normalizeSentimentCoefficient(nlpSource, si);
        var responseTokens = geoVisibilityCalculatorService.tokenizeResponseForMentions(nlpSource);
        List<String> needles = GeoVisibilityCalculatorService.splitBrandAliasPhrases(main, rawName);
        int nounCount = geoVisibilityCalculatorService.countNormalizedMentions(responseTokens, needles);
        int responseTokenLength = japaneseNlpService.totalTokenCount(nlpSource);
        double stuffingDensity = 0.0;
        for (String nd : needles) {
            stuffingDensity = StrictMath.max(stuffingDensity, japaneseNlpService.wordDensity(nlpSource, nd));
        }
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
        var som = gbvs.scorePercent();
        som = japaneseNlpService.applyIntensifierBoost(nlpSource, som);
        som = StrictMath.max(0.0, StrictMath.min(100.0, som));
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
                new LinkedHashMap<>());
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
