package com.geo.analytics.infrastructure.ai;

import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.service.JobStreamRegistryService;
import com.geo.analytics.application.service.SomScoreParser;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.model.SomRawMetrics;
import com.geo.analytics.domain.service.EntityNormalizer;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class GeminiVerificationAdapter implements AiVerificationPort {
    private static final Logger log = LoggerFactory.getLogger(GeminiVerificationAdapter.class);
    private final ChatLanguageModel chatLanguageModel;
    private final SomScoreParser somScoreParser;
    private final JobStreamRegistryService jobStreamRegistryService;
    private final GoogleAiGeminiStreamingChatModel geminiStreamingStandard;
    private final GoogleAiGeminiStreamingChatModel geminiStreamingPro;

    public GeminiVerificationAdapter(
            ChatLanguageModel chatLanguageModel,
            SomScoreParser somScoreParser,
            JobStreamRegistryService jobStreamRegistryService,
            @Qualifier(AiConfig.GEMINI_STREAMING_STANDARD) GoogleAiGeminiStreamingChatModel geminiStreamingStandard,
            @Qualifier(AiConfig.GEMINI_STREAMING_PRO) GoogleAiGeminiStreamingChatModel geminiStreamingPro) {
        this.chatLanguageModel = chatLanguageModel;
        this.somScoreParser = somScoreParser;
        this.jobStreamRegistryService = jobStreamRegistryService;
        this.geminiStreamingStandard = geminiStreamingStandard;
        this.geminiStreamingPro = geminiStreamingPro;
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
        return subscriptionPlan == SubscriptionPlan.PRO ? geminiStreamingPro : geminiStreamingStandard;
    }

    private VerificationResponse verifyWithoutJobStream(VerificationRequest verificationRequest, SubscriptionPlan plan) {
        try {
            String rawAiResponseJson = chatLanguageModel.chat(ChatRequest.builder()
                .messages(
                    SystemMessage.from(ConsultantPrompts.systemText(plan)),
                    UserMessage.from(buildUserContent(verificationRequest)))
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
        try {
            GoogleAiGeminiStreamingChatModel streamingChatModel = streamingModelForPlan(plan);
            List<ChatMessage> chatMessages = List.of(
                SystemMessage.from(ConsultantPrompts.systemText(plan)),
                UserMessage.from(buildUserContent(verificationRequest)));
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
        boolean isProPlan = subscriptionPlan == SubscriptionPlan.PRO;
        String resolved = EntityNormalizer.resolve(rawName, main, comps, isProPlan);
        boolean isProAnalysis = isProPlan;
        SomRawMetrics rawMetrics = new SomRawMetrics(tc, rp, si, isProAnalysis);
        double som = SomScoreCalculator.calculate(rawMetrics);
        boolean brand = tc > 0 || rp > 0;
        int overall = (int) Math.round(Math.clamp(som, 0.0, 100.0));
        return new VerificationResponse(
            rawAiResponseJson,
            som,
            brand,
            rp,
            overall,
            tc,
            rp,
            si,
            resolved);
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

    private static String buildUserContent(VerificationRequest verificationRequest) {
        String brandName = verificationRequest.brandName();
        String userQuery = verificationRequest.query();
        String crawledContent = verificationRequest.crawledContent();
        if (crawledContent != null && !crawledContent.isBlank()) {
            String sourceUrl = verificationRequest.url() != null ? verificationRequest.url() : "";
            String hash = verificationRequest.contentHash() != null ? verificationRequest.contentHash() : "";
            return """
                Brand under evaluation: %s
                User query: %s
                Based on the following extracted web page text, assess how the brand is represented for this query.
                Source URL: %s
                Content SHA-256: %s
                Extracted page text:
                %s
                """.formatted(brandName, userQuery, sourceUrl, hash, crawledContent);
        }
        return ConsultantPrompts.userTextBrandQueryOnly(brandName, userQuery);
    }
}
