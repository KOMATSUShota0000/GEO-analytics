package com.geo.analytics.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.CrawledPageData;
import com.geo.analytics.application.dto.RubricAuditResult;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.service.FactBasedScoreAggregator;
import com.geo.analytics.domain.service.MachineReadabilityScoreCalculator;
import com.geo.analytics.domain.service.MeoTrustScoreCalculator;
import com.geo.analytics.domain.service.RubricAiAuditScoreCalculator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JobAnalysisBenchmarkAssembler {
    private static final TypeReference<List<RubricAuditResult>> RUBRIC_LIST_TYPE = new TypeReference<>() {};
    private final ObjectMapper objectMapper;
    private final RubricBenchmarkGapService rubricBenchmarkGapService;

    public JobAnalysisBenchmarkAssembler(ObjectMapper objectMapper, RubricBenchmarkGapService rubricBenchmarkGapService) {
        this.objectMapper = objectMapper;
        this.rubricBenchmarkGapService = rubricBenchmarkGapService;
    }

    public BenchmarkAttach attach(JobEntity jobEntity) {
        if (jobEntity == null) {
            return new BenchmarkAttach(null, List.of(), null);
        }
        String selfJson = jobEntity.getSelfRubricAuditJson();
        if (selfJson == null || selfJson.isBlank()) {
            return new BenchmarkAttach(null, List.of(), null);
        }
        try {
            RubricAuditResult self = objectMapper.readValue(selfJson, RubricAuditResult.class);
            List<RubricAuditResult> competitors = parseCompetitors(jobEntity.getCompetitorRubricAuditsJson());
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
            List<String> gaps = rubricBenchmarkGapService.extractGaps(self, competitors);
            // 「AIが読みやすい構造」軸のサイト固有エビデンス（Schema.org/H1/robots等の実クロール所見）。
            String technicalEvidence = crawl != null ? crawl.seoTechnicalEvidenceSummary() : null;
            return new BenchmarkAttach(total, gaps, technicalEvidence);
        } catch (JsonProcessingException ex) {
            return new BenchmarkAttach(null, List.of(), null);
        }
    }

    private List<RubricAuditResult> parseCompetitors(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<RubricAuditResult> parsed = objectMapper.readValue(json, RUBRIC_LIST_TYPE);
        if (parsed == null) {
            return List.of();
        }
        ArrayList<RubricAuditResult> out = new ArrayList<>(parsed.size());
        for (int i = 0; i < parsed.size(); i++) {
            RubricAuditResult r = parsed.get(i);
            if (r != null) {
                out.add(r);
            }
        }
        return List.copyOf(out);
    }

    private CrawledPageData parseCrawl(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, CrawledPageData.class);
    }

    public record BenchmarkAttach(Double factBasedScore, List<String> rubricGaps, String technicalEvidence) {}
}
