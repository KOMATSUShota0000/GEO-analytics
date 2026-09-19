package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.service.FactBasedScoreAggregator;
import com.geo.analytics.domain.service.BrandMentionEngine;
import com.geo.analytics.domain.service.StuffingPenaltyCalculator;
import com.geo.analytics.domain.service.MachineReadabilityScoreCalculator;
import com.geo.analytics.domain.service.MeoTrustScoreCalculator;
import com.geo.analytics.domain.service.RubricAiAuditScoreCalculator;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JobAnalysisBenchmarkAssembler {
    private static final Logger log = LoggerFactory.getLogger(JobAnalysisBenchmarkAssembler.class);

    private final ObjectMapper objectMapper;
    private final BrandMentionEngine brandMentionEngine;

    public JobAnalysisBenchmarkAssembler(ObjectMapper objectMapper, BrandMentionEngine brandMentionEngine) {
        this.objectMapper = objectMapper;
        this.brandMentionEngine = brandMentionEngine;
    }

    public BenchmarkAttach attach(JobEntity jobEntity) {
        if (jobEntity == null) {
            return new BenchmarkAttach(null, null);
        }
        String selfJson = jobEntity.getSelfRubricAuditJson();
        if (selfJson == null || selfJson.isBlank()) {
            return new BenchmarkAttach(null, null);
        }
        try {
            RubricAuditResult self = objectMapper.readValue(selfJson, RubricAuditResult.class);
            CrawledPageData crawl = parseCrawl(jobEntity.getSelfCrawledPageJson());
            // Why: 自社サイト本文へのブランド名の詰め込みを、コンテンツ軸から減点する（#61 / `.cursorrules` 7節）。
            //      AI 回答（SoM の材料）には適用しない。言及が多いことは評価対象であり罰する理由がないため。
            double stuffingRetention = stuffingRetentionFor(jobEntity, crawl);
            double ai = RubricAiAuditScoreCalculator.scoreAiAudit(self) * stuffingRetention;
            Integer meoRc = jobEntity.getMeoReviewCount();
            int rc = meoRc != null && meoRc > 0 ? meoRc.intValue() : 0;
            Double meoStars = jobEntity.getMeoAverageStars();
            double avgStars = meoStars != null ? meoStars.doubleValue() : Double.NaN;
            double meo = MeoTrustScoreCalculator.scoreMeoTrust(rc, avgStars);
            boolean jsonLd = crawl != null && crawl.hasJsonLdSignal();
            boolean headings = crawl != null && crawl.headingHierarchyOk();
            double mr = MachineReadabilityScoreCalculator.score(jsonLd, headings);
            double total = FactBasedScoreAggregator.aggregate(ai, meo, mr);
            // 「AIが読みやすい構造」軸のサイト固有エビデンス（Schema.org/H1/robots等の実クロール所見）。
            String technicalEvidence = crawl != null ? crawl.seoTechnicalEvidenceSummary() : null;
            return new BenchmarkAttach(total, technicalEvidence);
        } catch (JsonProcessingException ex) {
            return new BenchmarkAttach(null, null);
        }
    }

    private double stuffingRetentionFor(JobEntity jobEntity, CrawledPageData crawl) {
        String content = crawl != null ? crawl.content() : null;
        String brandName = jobEntity.getBrandName();
        if (content == null || content.isBlank() || brandName == null || brandName.isBlank()) {
            return 1.0d;
        }
        double density = brandMentionEngine.measure(content, brandName).density();
        double retention = StuffingPenaltyCalculator.retentionFactor(density);
        if (retention < 1.0d) {
            log.info(
                    "stuffing_penalty jobId={} density={} retention={}",
                    jobEntity.getId(), density, retention);
        }
        return retention;
    }

    private CrawledPageData parseCrawl(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, CrawledPageData.class);
    }

    public record BenchmarkAttach(Double factBasedScore, String technicalEvidence) {}
}
