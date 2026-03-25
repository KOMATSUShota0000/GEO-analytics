package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.ConsultantOutputData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class SyncVerificationService {
    private final AiVerificationPort aiVerificationPort;
    private final SomScoreParser somScoreParser;
    private final WebCrawlerPort webCrawlerPort;

    public SyncVerificationService(
            AiVerificationPort aiVerificationPort,
            SomScoreParser somScoreParser,
            WebCrawlerPort webCrawlerPort) {
        this.aiVerificationPort = aiVerificationPort;
        this.somScoreParser = somScoreParser;
        this.webCrawlerPort = webCrawlerPort;
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
        VerificationRequest verificationRequest = new VerificationRequest(
            brandName,
            query,
            null,
            null,
            null,
            subscriptionPlan,
            jobId,
            queryId,
            canonicalMainBrand,
            registeredCompetitorBrands);
        VerificationResponse verificationResponse = aiVerificationPort.verify(verificationRequest);
        ConsultantOutputData consultantOutputData =
            somScoreParser.parseConsultantOutput(verificationResponse.rawResponseJson());
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
            verificationResponse.resolvedEntityLabel());
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
        CrawledPageData crawledPageData = webCrawlerPort.extractContent(url);
        VerificationRequest verificationRequest = new VerificationRequest(
            brandName,
            query,
            crawledPageData.url(),
            crawledPageData.content(),
            crawledPageData.contentHash(),
            subscriptionPlan,
            jobId,
            queryId,
            canonicalMainBrand,
            registeredCompetitorBrands);
        VerificationResponse verificationResponse = aiVerificationPort.verify(verificationRequest);
        ConsultantOutputData consultantOutputData =
            somScoreParser.parseConsultantOutput(verificationResponse.rawResponseJson());
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
            verificationResponse.resolvedEntityLabel());
    }
}
