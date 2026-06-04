package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.CompetitorExtractionMode;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.enums.RubricVerdictStatus;
import com.geo.analytics.domain.service.GeoVisibilityCalculatorService;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RubricGapAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RubricGapAnalysisService.class);
    private static final int STACK_TRACE_LIMIT = 20_000;
    private static final int MAX_GAPS = 5;

    private final AuditRubricResultRepository auditRubricResultRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final JobRepository jobRepository;

    public RubricGapAnalysisService(
            AuditRubricResultRepository auditRubricResultRepository,
            AuditHistoryRepository auditHistoryRepository,
            JobRepository jobRepository) {
        this.auditRubricResultRepository = auditRubricResultRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public List<String> identifyGaps(UUID auditHistoryId) {
        if (auditHistoryId == null) {
            throw new IllegalArgumentException("auditHistoryId");
        }
        List<AuditRubricResultEntity> rows;
        try {
            rows = auditRubricResultRepository.findByAuditHistoryId(auditHistoryId);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "rubric_gap_identify_load_failed auditHistoryId={} trace={}",
                    auditHistoryId,
                    truncateStackTrace(runtimeException));
            throw runtimeException;
        }
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, RubricVerdictStatus> selfVerdicts = new LinkedHashMap<>();
        LinkedHashMap<String, LinkedHashSet<RubricVerdictStatus>> competitorVerdicts = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            AuditRubricResultEntity row = rows.get(i);
            String criterion = row.getCriterionId();
            if (criterion == null || criterion.isBlank()) {
                continue;
            }
            if (!isLlmCriterion(criterion)) {
                continue;
            }
            RubricVerdictStatus verdict = parseVerdict(row.getVerdict());
            if (verdict == null) {
                continue;
            }
            if (row.isSelf()) {
                selfVerdicts.putIfAbsent(criterion, verdict);
            } else {
                competitorVerdicts
                        .computeIfAbsent(criterion, k -> new LinkedHashSet<>())
                        .add(verdict);
            }
        }
        ArrayList<String> gaps = new ArrayList<>(MAX_GAPS);
        for (Map.Entry<String, RubricVerdictStatus> entry : selfVerdicts.entrySet()) {
            if (gaps.size() >= MAX_GAPS) {
                break;
            }
            RubricVerdictStatus selfStatus = entry.getValue();
            if (selfStatus != RubricVerdictStatus.NO && selfStatus != RubricVerdictStatus.PARTIAL) {
                continue;
            }
            LinkedHashSet<RubricVerdictStatus> compSet = competitorVerdicts.get(entry.getKey());
            if (compSet == null || compSet.isEmpty()) {
                continue;
            }
            if (compSet.contains(RubricVerdictStatus.YES)) {
                gaps.add(entry.getKey());
            }
        }
        return List.copyOf(gaps);
    }

    @Transactional
    public double aggregateAndPersistFinalScore(UUID auditHistoryId) {
        if (auditHistoryId == null) {
            throw new IllegalArgumentException("auditHistoryId");
        }
        AuditHistoryEntity history = auditHistoryRepository
                .findById(auditHistoryId)
                .orElseThrow(() -> new EntityNotFoundException("auditHistoryId"));
        List<AuditRubricResultEntity> rows;
        try {
            rows = auditRubricResultRepository.findByAuditHistoryId(auditHistoryId);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "rubric_gap_aggregate_load_failed auditHistoryId={} trace={}",
                    auditHistoryId,
                    truncateStackTrace(runtimeException));
            throw runtimeException;
        }
        double aiAuditTotal = 0.0d;
        double meoTotal = 0.0d;
        double machineReadabilityTotal = 0.0d;
        for (int i = 0; i < rows.size(); i++) {
            AuditRubricResultEntity row = rows.get(i);
            if (!row.isSelf()) {
                continue;
            }
            RubricCriterionId criterion = parseCriterion(row.getCriterionId());
            if (criterion == null) {
                continue;
            }
            BigDecimal scoreBd = row.getScore();
            if (scoreBd == null) {
                continue;
            }
            double score = scoreBd.doubleValue();
            switch (criterion.source()) {
                case LLM -> aiAuditTotal = StrictMath.fma(score, 1.0d, aiAuditTotal);
                case SYSTEM -> machineReadabilityTotal = StrictMath.fma(score, 1.0d, machineReadabilityTotal);
                case MEO -> meoTotal = StrictMath.fma(score, 1.0d, meoTotal);
            }
        }
        CompetitorExtractionMode mode = jobRepository.findById(history.getJobId())
                .map(JobEntity::getCompetitorExtractionMode)
                .orElse(CompetitorExtractionMode.LOCAL_STORE);
        double finalScore = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                aiAuditTotal, meoTotal, machineReadabilityTotal, mode);
        double rounded = BigDecimal.valueOf(finalScore)
                .setScale(3, RoundingMode.HALF_EVEN)
                .doubleValue();
        history.setSomScore(rounded);
        history.setGbvsNormalizedScore(rounded);
        history.setCalculationVersion(GeoVisibilityCalculatorService.CALCULATION_VERSION);
        auditHistoryRepository.save(history);
        return rounded;
    }

    private static boolean isLlmCriterion(String criterionId) {
        try {
            return RubricCriterionId.valueOf(criterionId).source() == RubricCriterionId.Source.LLM;
        } catch (IllegalArgumentException illegalArgumentException) {
            return false;
        }
    }

    private static RubricCriterionId parseCriterion(String criterionId) {
        if (criterionId == null) {
            return null;
        }
        try {
            return RubricCriterionId.valueOf(criterionId);
        } catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    private static RubricVerdictStatus parseVerdict(String verdict) {
        if (verdict == null) {
            return null;
        }
        try {
            return RubricVerdictStatus.valueOf(verdict);
        } catch (IllegalArgumentException illegalArgumentException) {
            return null;
        }
    }

    private static String truncateStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }
}
