package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.GeoOnboardingLlmResult;
import com.geo.analytics.infrastructure.config.AiConfig;
import com.geo.analytics.domain.ai.CitationValidator;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.prompt.DebatePersonaSystemPrompts;
import com.geo.analytics.domain.enums.IndustryType;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.domain.logic.CalibrationCalculator;
import com.geo.analytics.domain.logic.ConvergenceController;
import com.geo.analytics.domain.logic.DebateTextMathHeuristics;
import com.geo.analytics.domain.logic.InformationGainCalculator;
import com.geo.analytics.application.security.PromptInjectionValidator;
import com.geo.analytics.domain.logic.GeoEvidenceRanker;
import com.geo.analytics.domain.logic.GeoRagEvidenceXmlBuilder;
import com.geo.analytics.domain.logic.CompetitorEvidenceBudget;
import com.geo.analytics.domain.model.MinorityReport;
import com.geo.analytics.domain.model.GeoRagEvidence;
import com.geo.analytics.domain.model.GeoEvidenceRow;
import com.geo.analytics.infrastructure.ai.DirectorOnboardingJson;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import com.geo.analytics.web.dto.DebateOnboardingSseEvent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DebateOnboardingOrchestrator {
    /** オンボーディングディベートの最大ターン数（クレジット按分の分母と一致させる）。 */
    public static final int MAX_DEBATE_TURNS = 5;
    /** 監査・WORM 向け実況バッファの上限（超過分は古いものから破棄）。 */
    public static final int MAX_NARRATION_LOG_BUFFER_ENTRIES = 100;
    private static final int MAX_AUDIT_NARRATION_MESSAGE_CHARS = 2000;
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
    private final GeoEvidenceRanker geoEvidenceRanker;
    private final PromptInjectionValidator promptInjectionValidator;
    private final DebateMathAuditService debateMathAuditService;
    private final OnboardingDebateStreamRegistry onboardingDebateStreamRegistry;

    public DebateOnboardingOrchestrator(
            @Qualifier(AiConfig.GEMINI_DEBATE_ANALYST) ChatLanguageModel analystChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_INNOVATOR) ChatLanguageModel innovatorChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_SKEPTIC) ChatLanguageModel skepticChatModel,
            @Qualifier(AiConfig.GEMINI_DEBATE_DIRECTOR) ChatLanguageModel directorChatModel,
            ObjectMapper objectMapper,
            GeoEvidenceRanker geoEvidenceRanker,
            PromptInjectionValidator promptInjectionValidator,
            DebateMathAuditService debateMathAuditService,
            OnboardingDebateStreamRegistry onboardingDebateStreamRegistry) {
        this.analystChatModel = analystChatModel;
        this.innovatorChatModel = innovatorChatModel;
        this.skepticChatModel = skepticChatModel;
        this.directorChatModel = directorChatModel;
        this.objectMapper = objectMapper;
        this.geoEvidenceRanker = Objects.requireNonNull(geoEvidenceRanker, "geoEvidenceRanker");
        this.promptInjectionValidator =
                Objects.requireNonNull(promptInjectionValidator, "promptInjectionValidator");
        this.debateMathAuditService = Objects.requireNonNull(debateMathAuditService, "debateMathAuditService");
        this.onboardingDebateStreamRegistry =
                Objects.requireNonNull(onboardingDebateStreamRegistry, "onboardingDebateStreamRegistry");
    }

    public static void enforceNarrationLogBufferCap(Deque<DebateOnboardingSseEvent> narrationLogBuffer) {
        Objects.requireNonNull(narrationLogBuffer, "narrationLogBuffer");
        // ConcurrentLinkedDeque は弱整合性のため synchronized 不要。size() は O(n) だが
        // キャップ（小さい固定上限）以下では実質的なコストにならず、pollFirst() で
        // 旧 remove(0) と同じ FIFO 先頭削除挙動を維持する。
        while (narrationLogBuffer.size() >= MAX_NARRATION_LOG_BUFFER_ENTRIES) {
            if (narrationLogBuffer.pollFirst() == null) {
                break;
            }
        }
    }

    /** 監査ログ／JSONB 向けにユーザ向け実況メッセージを短縮する（SSE ペイロードは別途フル長を可）。 */
    public static String truncateNarrationForAudit(String message) {
        if (message == null) {
            return null;
        }
        if (message.length() <= MAX_AUDIT_NARRATION_MESSAGE_CHARS) {
            return message;
        }
        return message.substring(0, MAX_AUDIT_NARRATION_MESSAGE_CHARS) + "…";
    }

    public GeoOnboardingLlmResult runDebateOnboarding(
            String plainText,
            UUID targetId,
            String searchQuery,
            List<GeoEvidenceRow> rawSeoRows,
            IndustryType industryHint,
            UUID sessionId,
            AtomicInteger executedTurns,
            Deque<DebateOnboardingSseEvent> narrationLogBuffer) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(executedTurns, "executedTurns");
        Objects.requireNonNull(narrationLogBuffer, "narrationLogBuffer");

        String sessionOutcome = "SUCCESS";
        GeoOnboardingLlmResult resultHolder = null;
        int completedRounds = 0;
        String stopReason = "MAX_TURNS_REACHED";
        ConvergenceController.ConvergenceSnapshot lastSnapshot = null;
        double lastGeoIg = 0.0d;
        double lastTrust = 0.0d;
        boolean competitorXmlIncluded = false;
        List<GeoRagEvidence> usedEvidencesForAudit = List.of();

        try {
            IndustryType resolvedIndustry = industryHint != null ? industryHint : IndustryType.OTHER;
            String q = searchQuery == null ? "" : searchQuery;
            String wrapped = "<scraped_data>\n" + plainText + "\n</scraped_data>";
            List<GeoRagEvidence> seoEvidences = List.of();
            if (rawSeoRows != null && !rawSeoRows.isEmpty()) {
                emitAndRecord(
                        targetId,
                        sessionId,
                        narrationLogBuffer,
                        DebateOnboardingSseEvent.DebateOnboardingSseEventType.NARRATION,
                        DebateOnboardingSseEvent.DebateStreamPersona.SYSTEM,
                        DebateOnboardingSseEvent.DebateStreamPhase.GATHERING,
                        "AI推奨視点に基づき、参照候補の根拠スニペットを収集・正規化しています。",
                        null);
                seoEvidences =
                        geoEvidenceRanker.provideEvidences(
                                q,
                                rawSeoRows,
                                RAG_EVIDENCE_MAX_COUNT,
                                GeoEvidenceRanker.DEFAULT_MAX_PER_DOMAIN,
                                DEFAULT_EVIDENCE_RELEVANCE_LABEL);
                SubscriptionPlan subscriptionPlan =
                        TenantPlanScope.currentSubscriptionPlan().orElse(SubscriptionPlan.STANDARD);
                int maxCompetitorXmlChars = subscriptionPlan.maxCompetitorEvidenceXmlChars();
                seoEvidences = CompetitorEvidenceBudget.clipEvidences(seoEvidences, maxCompetitorXmlChars);
            }
            String competitorXml =
                    (seoEvidences == null || seoEvidences.isEmpty())
                            ? ""
                            : GeoRagEvidenceXmlBuilder.buildCompetitorBlock(seoEvidences);
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
            emitAndRecord(
                    targetId,
                    sessionId,
                    narrationLogBuffer,
                    DebateOnboardingSseEvent.DebateOnboardingSseEventType.NARRATION,
                    DebateOnboardingSseEvent.DebateStreamPersona.ANALYST,
                    DebateOnboardingSseEvent.DebateStreamPhase.ANALYZING,
                    "ページ実体と外部シグナルを結合しました。市場セグメントとリスク要因の整理に入ります。",
                    null);
            StringBuilder debateAccumulator = new StringBuilder();
            double[] prevConfidences = null;
            double[] prevCentroid = null;
            String analystText = "";
            String innovatorRaw = "";
            String innovatorForHistory = "";
            String skepticText = "";

            for (int turn = 0; turn < MAX_DEBATE_TURNS; turn++) {
            throwIfClientDisconnected();
            String userForAnalyst = contextAnalystBase;
            String userForInnovator = contextInnovatorBase;
            if (debateAccumulator.length() > 0) {
                String accumulated =
                        "\n\n## これまでの議論の蓄積\n" + debateAccumulator;
                userForAnalyst = contextAnalystBase + accumulated;
                userForInnovator = contextInnovatorBase + accumulated;
            }
            throwIfClientDisconnected();
            analystText =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.ANALYST, resolvedIndustry),
                            userForAnalyst,
                            analystChatModel);
            throwIfClientDisconnected();
            emitAndRecord(
                    targetId,
                    sessionId,
                    narrationLogBuffer,
                    DebateOnboardingSseEvent.DebateOnboardingSseEventType.NARRATION,
                    DebateOnboardingSseEvent.DebateStreamPersona.INNOVATOR,
                    DebateOnboardingSseEvent.DebateStreamPhase.DEBATING,
                    "競合ブロックは遮断済みです。ページ本文のみを手掛かりに、独自の付加仮説を蒸留します。",
                    null);
            throwIfClientDisconnected();
            innovatorRaw =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.INNOVATOR, resolvedIndustry),
                            userForInnovator,
                            innovatorChatModel);
            throwIfClientDisconnected();
            innovatorForHistory = innovatorRaw;
            if (!CitationValidator.hasValidCitation(innovatorRaw)) {
                log.warn("innovator output rejected: insufficient citation format");
                innovatorForHistory = REJECTED_INNOVATOR;
            }
            String skepticInput = buildSkepticUserMessage(analystText, innovatorForHistory, debateAccumulator);
            throwIfClientDisconnected();
            skepticText =
                    singleChat(
                            DebatePersonaSystemPrompts.forPersona(DebatePersona.SKEPTIC, resolvedIndustry),
                            skepticInput,
                            skepticChatModel);
            throwIfClientDisconnected();
            executedTurns.incrementAndGet();

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
            boolean convergedThisRound = false;
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
                    convergedThisRound = true;
                }
            }

            emitAndRecord(
                    targetId,
                    sessionId,
                    narrationLogBuffer,
                    DebateOnboardingSseEvent.DebateOnboardingSseEventType.SCORE_UPDATE,
                    DebateOnboardingSseEvent.DebateStreamPersona.SKEPTIC,
                    DebateOnboardingSseEvent.DebateStreamPhase.DEBATING,
                    "数理カーネルから分布を更新しました。前提の健全性と反証可能性を確認します。",
                    partialScoresPayload(math, turn + 1, lastGeoIg, lastSnapshot));

            prevConfidences = Arrays.copyOf(currConfidences, currConfidences.length);
            prevCentroid = Arrays.copyOf(currCentroid, currCentroid.length);
            completedRounds = turn + 1;
            if (convergedThisRound) {
                break;
            }
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
        emitAndRecord(
                targetId,
                sessionId,
                narrationLogBuffer,
                DebateOnboardingSseEvent.DebateOnboardingSseEventType.NARRATION,
                DebateOnboardingSseEvent.DebateStreamPersona.DIRECTOR,
                DebateOnboardingSseEvent.DebateStreamPhase.CONVERGING,
                "GEO-IG と較正信頼を踏まえ、合意可能な最終成果物へ収束させます。",
                null);
        throwIfClientDisconnected();
        String rawJson = singleChat(directorSystem, directorInput, directorChatModel);

        throwIfClientDisconnected();
        resultHolder = mapDirectorToResult(rawJson);
        } catch (Throwable t) {
            if (t instanceof CancellationException) {
                sessionOutcome = "CLIENT_DISCONNECT";
            } else {
                sessionOutcome = "FAILED";
            }
            throw t;
        } finally {
            try {
                debateMathAuditService.saveOnboardingAudit(
                        targetId,
                        completedRounds,
                        stopReason,
                        lastSnapshot,
                        lastGeoIg,
                        lastTrust,
                        CONVERGENCE_FRICTION,
                        competitorXmlIncluded,
                        usedEvidencesForAudit,
                        narrationLogBuffer,
                        sessionOutcome,
                        executedTurns.get());
            } catch (RuntimeException e) {
                log.warn("saveOnboardingAudit failed targetId={}", targetId, e);
            }
        }
        return resultHolder;
    }

    private static void throwIfClientDisconnected() {
        if (Thread.currentThread().isInterrupted()) {
            log.debug("debate onboarding cancelled (client disconnected)");
            throw new CancellationException("onboarding client disconnected");
        }
    }

    private void emitAndRecord(
            UUID targetId,
            UUID sessionId,
            Deque<DebateOnboardingSseEvent> narrationLogBuffer,
            DebateOnboardingSseEvent.DebateOnboardingSseEventType eventType,
            DebateOnboardingSseEvent.DebateStreamPersona persona,
            DebateOnboardingSseEvent.DebateStreamPhase phase,
            String message,
            DebateOnboardingSseEvent.DebatePartialScoresPayload partialScoresForWire) {
        if (sessionId == null) {
            return;
        }
        Instant timestamp = Instant.now();
        DebateOnboardingSseEvent wireEvent =
                new DebateOnboardingSseEvent(
                        eventType, persona, phase, message, partialScoresForWire, timestamp, sessionId);
        DebateOnboardingSseEvent bufferEvent;
        if (eventType == DebateOnboardingSseEvent.DebateOnboardingSseEventType.SCORE_UPDATE
                && partialScoresForWire != null) {
            ScoreAuditBufferFields snap = summarizeScoresForAudit(partialScoresForWire, message);
            bufferEvent =
                    new DebateOnboardingSseEvent(
                            eventType,
                            persona,
                            phase,
                            snap.auditMessage(),
                            snap.summarizedPayload(),
                            timestamp,
                            sessionId);
        } else {
            bufferEvent =
                    new DebateOnboardingSseEvent(
                            eventType,
                            persona,
                            phase,
                            truncateNarrationForAudit(message),
                            null,
                            timestamp,
                            sessionId);
        }
        enforceNarrationLogBufferCap(narrationLogBuffer);
        narrationLogBuffer.add(bufferEvent);
        try {
            onboardingDebateStreamRegistry.sendEvent(targetId, sessionId, wireEvent);
        } catch (Throwable throwable) {
            log.debug("onboarding debate narration skipped: {}", throwable.toString());
        }
    }

    private record ScoreAuditBufferFields(
            DebateOnboardingSseEvent.DebatePartialScoresPayload summarizedPayload,
            String auditMessage) {}

    private static ScoreAuditBufferFields summarizeScoresForAudit(
            DebateOnboardingSseEvent.DebatePartialScoresPayload full,
            String scoreEventMessage) {
        String auditMessage = truncateNarrationForAudit(scoreEventMessage);
        DebateOnboardingSseEvent.DebatePartialScoresPayload summarized =
                new DebateOnboardingSseEvent.DebatePartialScoresPayload(
                        full.round(),
                        null,
                        null,
                        full.sDensity(),
                        full.qIntent(),
                        null,
                        null,
                        full.geoIg(),
                        full.wasserstein1());
        return new ScoreAuditBufferFields(summarized, auditMessage);
    }

    private static DebateOnboardingSseEvent.DebatePartialScoresPayload partialScoresPayload(
            DebateTextMathHeuristics.RoundHeuristicResult math,
            int round,
            double geoIg,
            ConvergenceController.ConvergenceSnapshot snapshot) {
        Double wasserstein1 = null;
        if (snapshot != null) {
            double w1 = snapshot.wasserstein1();
            wasserstein1 = Double.isFinite(w1) ? w1 : null;
        }
        return new DebateOnboardingSseEvent.DebatePartialScoresPayload(
                round,
                doubleArrayToList(math.pSite()),
                doubleArrayToList(math.agentMass()),
                math.sDensity(),
                math.qIntent(),
                doubleArrayToList(math.currConfidences()),
                doubleArrayToList(math.currCentroid()),
                geoIg,
                wasserstein1);
    }

    private static List<Double> doubleArrayToList(double[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return Arrays.stream(values).boxed().toList();
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
