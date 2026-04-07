package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CompetitorScoreRow;
import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.port.WebCrawlerPort;
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
    private final WebCrawlerPort webCrawlerPort;
    private final DomainTrustService domainTrustService;
    private final JsonbOperations jsonbOperations;

    public SyncVerificationService(
            AiVerificationPort aiVerificationPort,
            SomScoreParser somScoreParser,
            WebCrawlerPort webCrawlerPort,
            DomainTrustService domainTrustService,
            JsonbOperations jsonbOperations) {
        this.aiVerificationPort = aiVerificationPort;
        this.somScoreParser = somScoreParser;
        this.webCrawlerPort = webCrawlerPort;
        this.domainTrustService = domainTrustService;
        this.jsonbOperations = jsonbOperations;
    }

    public SyncVerificationResult verify(String brandName, String query, SubscriptionPlan subscriptionPlan) {
        return verify(brandName, query, subscriptionPlan, null, null, null, null);
    }

    public SyncVerificationResult verify(
            String brandName,
            String query,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId) {
        return verify(brandName, query, subscriptionPlan, jobId, queryId, null, null);
    }

    public SyncVerificationResult verify(
            String brandName,
            String query,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId,
            String canonicalMainBrand,
            List<String> registeredCompetitorBrands) {
        var verificationRequest = domainTrustService.applyDomainPolicy(new VerificationRequest(
                brandName,
                query,
                null,
                null,
                null,
                subscriptionPlan,
                jobId,
                queryId,
                canonicalMainBrand,
                registeredCompetitorBrands,
                null));
        var verificationResponse = aiVerificationPort.verify(verificationRequest);
        return toResult(verificationRequest, verificationResponse);
    }

    public SyncVerificationResult verifyWithUrl(
            String brandName,
            String query,
            String url,
            SubscriptionPlan subscriptionPlan) {
        return verifyWithUrl(brandName, query, url, subscriptionPlan, null, null, null, null);
    }

    public SyncVerificationResult verifyWithUrl(
            String brandName,
            String query,
            String url,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId) {
        return verifyWithUrl(brandName, query, url, subscriptionPlan, jobId, queryId, null, null);
    }

    public SyncVerificationResult verifyWithUrl(
            String brandName,
            String query,
            String url,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId,
            String canonicalMainBrand,
            List<String> registeredCompetitorBrands) {
        var crawledPageData = webCrawlerPort.extractContent(url);
        var verificationRequest = domainTrustService.applyDomainPolicy(new VerificationRequest(
                brandName,
                query,
                crawledPageData.url(),
                crawledPageData.content(),
                crawledPageData.contentHash(),
                subscriptionPlan,
                jobId,
                queryId,
                canonicalMainBrand,
                registeredCompetitorBrands,
                null));
        var verificationResponse = aiVerificationPort.verify(verificationRequest);
        return toResult(verificationRequest, verificationResponse);
    }

    private SyncVerificationResult toResult(VerificationRequest appliedRequest, VerificationResponse verificationResponse) {
        var content = appliedRequest.crawledContent();
        int analysisTextLength = content != null ? content.length() : 0;
        var consultantOutputData =
                somScoreParser.parseConsultantOutput(verificationResponse.rawResponseJson());
        var rows = verificationResponse.competitorResults().stream()
                .map(cr -> new CompetitorScoreRow(
                        cr.competitorLabel(),
                        cr.somScore() != null ? cr.somScore() : 0.0,
                        cr.rankPosition(),
                        cr.visibilityStage(),
                        cr.matchStatus(),
                        cr.nounCount()))
                .toList();
        var insightsJson = serializeInsights(verificationResponse);
        return new SyncVerificationResult(
                verificationResponse.rawResponseJson(),
                verificationResponse.somScore(),
                verificationResponse.brandMentioned(),
                verificationResponse.mentionRank(),
                verificationResponse.overallScore(),
                verificationResponse.tokenCount(),
                verificationResponse.rankPosition(),
                verificationResponse.sentimentIntensity(),
                consultantOutputData.response(),
                verificationResponse.resolvedEntityLabel(),
                verificationResponse.visibilityStage(),
                verificationResponse.modifiedZScore(),
                verificationResponse.calculationVersion(),
                rows,
                insightsJson,
                analysisTextLength);
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
