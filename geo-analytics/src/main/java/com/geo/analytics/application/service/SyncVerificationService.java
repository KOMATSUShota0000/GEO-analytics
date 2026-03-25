package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.SomScoreData;
import com.geo.analytics.application.dto.SyncVerificationResult;
import com.geo.analytics.application.dto.VerificationRequest;
import com.geo.analytics.application.dto.VerificationResponse;
import com.geo.analytics.application.port.AiVerificationPort;
import com.geo.analytics.application.port.WebCrawlerPort;
import com.geo.analytics.domain.enums.SubscriptionPlan;
import org.springframework.stereotype.Service;
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
        return verify(brandName, query, subscriptionPlan, null, null);
    }

    public SyncVerificationResult verify(
            String brandName,
            String query,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId) {
        VerificationRequest verificationRequest = new VerificationRequest(
            brandName,
            query,
            null,
            null,
            null,
            subscriptionPlan,
            jobId,
            queryId);
        VerificationResponse verificationResponse = aiVerificationPort.verify(verificationRequest);
        SomScoreData parsedSomScoreData = somScoreParser.parse(verificationResponse.rawResponseJson());
        return new SyncVerificationResult(
            verificationResponse.rawResponseJson(),
            verificationResponse.somScore(),
            verificationResponse.brandMentioned(),
            verificationResponse.mentionRank(),
            parsedSomScoreData.response());
    }

    public SyncVerificationResult verifyWithUrl(
            String brandName,
            String query,
            String url,
            SubscriptionPlan subscriptionPlan) {
        return verifyWithUrl(brandName, query, url, subscriptionPlan, null, null);
    }

    public SyncVerificationResult verifyWithUrl(
            String brandName,
            String query,
            String url,
            SubscriptionPlan subscriptionPlan,
            UUID jobId,
            UUID queryId) {
        CrawledPageData crawledPageData = webCrawlerPort.extractContent(url);
        VerificationRequest verificationRequest = new VerificationRequest(
            brandName,
            query,
            crawledPageData.url(),
            crawledPageData.content(),
            crawledPageData.contentHash(),
            subscriptionPlan,
            jobId,
            queryId);
        VerificationResponse verificationResponse = aiVerificationPort.verify(verificationRequest);
        SomScoreData parsedSomScoreData = somScoreParser.parse(verificationResponse.rawResponseJson());
        return new SyncVerificationResult(
            verificationResponse.rawResponseJson(),
            verificationResponse.somScore(),
            verificationResponse.brandMentioned(),
            verificationResponse.mentionRank(),
            parsedSomScoreData.response());
    }
}
