package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.persistence.JsonbOperations;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class SyncVerificationService {
    private final AiVerificationPort aiVerificationPort;
    private final SomScoreParser somScoreParser;
    private final JsonbOperations jsonbOperations;

    public SyncVerificationService(
            AiVerificationPort aiVerificationPort,
            SomScoreParser somScoreParser,
            JsonbOperations jsonbOperations) {
        this.aiVerificationPort = aiVerificationPort;
        this.somScoreParser = somScoreParser;
        this.jsonbOperations = jsonbOperations;
    }

    public SyncVerificationResult verify(String brandName, String query, SubscriptionPlan subscriptionPlan) {
        return verify(brandName, query, subscriptionPlan, null, null, null);
    }

    public SyncVerificationResult verify(
            String brandName,
            String query,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId) {
        return verify(brandName, query, subscriptionPlan, jobId, queryId, null);
    }

    public SyncVerificationResult verify(
            String brandName,
            String query,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId,
            String canonicalMainBrand) {
        // Why: 材料なし（推定）で検証する。クロール本文を材料にする経路は撤去した（ADR-058）。
        var verificationRequest = new VerificationRequest(
                brandName,
                query,
                null,
                subscriptionPlan,
                jobId,
                queryId,
                canonicalMainBrand,
                null);
        var verificationResponse = aiVerificationPort.verify(verificationRequest);
        return toResult(verificationRequest, verificationResponse);
    }

    /**
     * 実測の AI Overview 本文を材料に検証する（#92 / ADR-039）。
     *
     * <p>Why: クロールした自社サイト本文は「AI 回答内での見え方」の材料にならない。この経路ではクロールを行わず、
     * 材料は AI Overview 本文のみとする。取得できなかったクエリは材料なし（推定）で検証する。url は引き続き渡す
     * （ドメイン由来の重み付けが従来どおり効くようにするため。重み自体の撤去は #60）。
     */
    public SyncVerificationResult verifyWithAiOverview(
            String brandName,
            String query,
            String url,
            String aiOverviewText,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId,
            String canonicalMainBrand) {
        var verificationRequest = new VerificationRequest(
                brandName,
                query,
                url,
                subscriptionPlan,
                jobId,
                queryId,
                canonicalMainBrand,
                aiOverviewText);
        var verificationResponse = aiVerificationPort.verify(verificationRequest);
        return toResult(verificationRequest, verificationResponse);
    }

    private SyncVerificationResult toResult(VerificationRequest appliedRequest, VerificationResponse verificationResponse) {
        // Why: 解析に使った材料の長さ。材料は実測の AI Overview 本文で、取れなかったクエリは 0（ADR-058）。
        var material = appliedRequest.aiOverviewText();
        int analysisTextLength = material != null ? material.length() : 0;
        var consultantOutputData =
                somScoreParser.parseConsultantOutput(verificationResponse.rawResponseJson());
        var insightsJson = serializeInsights(verificationResponse);
        return new SyncVerificationResult(
                verificationResponse.rawResponseJson(),
                verificationResponse.somScore(),
                verificationResponse.brandMentioned(),
                verificationResponse.mentionRank(),
                verificationResponse.overallScore(),
                verificationResponse.tokenCount(),
                verificationResponse.aiCitationPosition(),
                verificationResponse.sentimentIntensity(),
                consultantOutputData.response(),
                verificationResponse.resolvedEntityLabel(),
                verificationResponse.visibilityStage(),
                verificationResponse.modifiedZScore(),
                verificationResponse.calculationVersion(),
                insightsJson,
                verificationResponse.gbvsNormalizedScore(),
                analysisTextLength,
                verificationResponse.competitorResults());
    }

    private String serializeInsights(VerificationResponse verificationResponse) {
        if (verificationResponse.modelInsights().isEmpty()) {
            return null;
        }
        var map = new LinkedHashMap<String, String>();
        for (var e : verificationResponse.modelInsights().entrySet()) {
            map.put(e.getKey().name(), e.getValue());
        }
        return jsonbOperations.serialize(map);
    }
}
