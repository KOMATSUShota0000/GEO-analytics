package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.BusinessModelType;
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
import java.util.Comparator;
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
        // Why: 旧実装は「自社が未達 かつ 競合が達成」の相対比較のみをギャップとしていたが、競合サイトの
        //      ルーブリック監査は Sprint C5 で撤去済みで非自社行が生成されない（audit_rubric_results は
        //      全行 is_self=true）。そのため常に空を返し、後続の改善タスク生成が一度も動かなかった。
        //      判定を「自社が未達」という絶対条件へ変更し、競合行は順序付けの材料として扱う。
        //      競合監査が復活した際（DEBATE-9）は compSet の有無がそのまま優先度に効くため、
        //      この構造のまま相対ギャップを取り戻せる。
        ArrayList<String> gaps = new ArrayList<>(selfVerdicts.size());
        for (Map.Entry<String, RubricVerdictStatus> entry : selfVerdicts.entrySet()) {
            RubricVerdictStatus selfStatus = entry.getValue();
            if (selfStatus != RubricVerdictStatus.NO && selfStatus != RubricVerdictStatus.PARTIAL) {
                continue;
            }
            gaps.add(entry.getKey());
        }
        gaps.sort(Comparator.comparingInt(criterion -> gapPriority(criterion, selfVerdicts, competitorVerdicts)));
        return gaps.size() <= MAX_GAPS ? List.copyOf(gaps) : List.copyOf(gaps.subList(0, MAX_GAPS));
    }

    /**
     * ギャップの深刻度。小さいほど優先。競合が達成している基準（相対的な劣後）を最優先し、
     * 同条件なら未達（NO）を部分達成（PARTIAL）より前に置く。
     */
    private static int gapPriority(
            String criterion,
            Map<String, RubricVerdictStatus> selfVerdicts,
            Map<String, LinkedHashSet<RubricVerdictStatus>> competitorVerdicts) {
        LinkedHashSet<RubricVerdictStatus> compSet = competitorVerdicts.get(criterion);
        boolean competitorAchieved = compSet != null && compSet.contains(RubricVerdictStatus.YES);
        boolean selfUnmet = selfVerdicts.get(criterion) == RubricVerdictStatus.NO;
        return (competitorAchieved ? 0 : 2) + (selfUnmet ? 0 : 1);
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
        double thirdPartyCoreTotal = 0.0d;
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
                case AUTHORITY -> thirdPartyCoreTotal = StrictMath.fma(score, 1.0d, thirdPartyCoreTotal);
            }
        }
        BusinessModelType mode = jobRepository.findById(history.getJobId())
                .map(JobEntity::getBusinessModelType)
                .orElse(BusinessModelType.LOCAL_STORE);
        double authority = GeoVisibilityCalculatorService.combineAuthority(thirdPartyCoreTotal, meoTotal, mode);
        double finalScore = GeoVisibilityCalculatorService.calculateFinalGeoScore(
                aiAuditTotal, machineReadabilityTotal, authority);
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
