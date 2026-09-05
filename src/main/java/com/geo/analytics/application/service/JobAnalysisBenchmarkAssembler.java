package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.service.FactBasedScoreAggregator;
import com.geo.analytics.domain.service.MachineReadabilityScoreCalculator;
import com.geo.analytics.domain.service.MeoTrustScoreCalculator;
import com.geo.analytics.domain.service.RubricAiAuditScoreCalculator;
import org.springframework.stereotype.Component;

@Component
public class JobAnalysisBenchmarkAssembler {
    private final ObjectMapper objectMapper;

    public JobAnalysisBenchmarkAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
            double ai = RubricAiAuditScoreCalculator.scoreAiAudit(self);
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

    private CrawledPageData parseCrawl(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, CrawledPageData.class);
    }

    public record BenchmarkAttach(Double factBasedScore, String technicalEvidence) {}
}
