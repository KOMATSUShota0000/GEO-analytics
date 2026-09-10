package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.entity.AuditRubricResultEntity;
import com.geo.analytics.domain.enums.RubricCriterionId;
import com.geo.analytics.domain.enums.RubricVerdictStatus;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RubricGapAnalysisServiceTest {

    private static final UUID AUDIT_HISTORY_ID = UUID.randomUUID();

    private static AuditRubricResultEntity row(
            RubricCriterionId criterion, RubricVerdictStatus verdict, boolean self) {
        var entity = new AuditRubricResultEntity();
        entity.setAuditHistoryId(AUDIT_HISTORY_ID);
        entity.setCriterionId(criterion.name());
        entity.setVerdict(verdict.name());
        entity.setSelf(self);
        return entity;
    }

    private static RubricGapAnalysisService serviceReturning(List<AuditRubricResultEntity> rows) {
        var rubricRepository = mock(AuditRubricResultRepository.class);
        when(rubricRepository.findByAuditHistoryId(any())).thenReturn(rows);
        return new RubricGapAnalysisService(
                rubricRepository, mock(AuditHistoryRepository.class), mock(JobRepository.class));
    }

    @Test
    void 競合行が無くても自社が未達の基準はギャップになる() {
        var service = serviceReturning(List.of(
                row(RubricCriterionId.DIRECT_ANSWER_FIRST, RubricVerdictStatus.NO, true),
                row(RubricCriterionId.ATOMIC_FACTS, RubricVerdictStatus.PARTIAL, true),
                row(RubricCriterionId.FAQ_PRESENCE, RubricVerdictStatus.YES, true)));

        assertThat(service.identifyGaps(AUDIT_HISTORY_ID))
                .containsExactly(
                        RubricCriterionId.DIRECT_ANSWER_FIRST.name(), RubricCriterionId.ATOMIC_FACTS.name());
    }

    @Test
    void 達成済みの基準はギャップに含まれない() {
        var service = serviceReturning(List.of(
                row(RubricCriterionId.DIRECT_ANSWER_FIRST, RubricVerdictStatus.YES, true),
                row(RubricCriterionId.ATOMIC_FACTS, RubricVerdictStatus.YES, true)));

        assertThat(service.identifyGaps(AUDIT_HISTORY_ID)).isEmpty();
    }

    @Test
    void 競合が達成している基準が先に並ぶ() {
        var service = serviceReturning(List.of(
                row(RubricCriterionId.DIRECT_ANSWER_FIRST, RubricVerdictStatus.PARTIAL, true),
                row(RubricCriterionId.ATOMIC_FACTS, RubricVerdictStatus.PARTIAL, true),
                row(RubricCriterionId.ATOMIC_FACTS, RubricVerdictStatus.YES, false)));

        assertThat(service.identifyGaps(AUDIT_HISTORY_ID))
                .containsExactly(
                        RubricCriterionId.ATOMIC_FACTS.name(), RubricCriterionId.DIRECT_ANSWER_FIRST.name());
    }

    @Test
    void 未達は部分達成より先に並ぶ() {
        var service = serviceReturning(List.of(
                row(RubricCriterionId.DIRECT_ANSWER_FIRST, RubricVerdictStatus.PARTIAL, true),
                row(RubricCriterionId.ATOMIC_FACTS, RubricVerdictStatus.NO, true)));

        assertThat(service.identifyGaps(AUDIT_HISTORY_ID))
                .containsExactly(
                        RubricCriterionId.ATOMIC_FACTS.name(), RubricCriterionId.DIRECT_ANSWER_FIRST.name());
    }

    @Test
    void ギャップは上限5件で打ち切られる() {
        var llmCriteria = RubricCriterionId.llmCriteria();
        var rows = llmCriteria.stream().map(c -> row(c, RubricVerdictStatus.NO, true)).toList();
        var service = serviceReturning(rows);

        assertThat(llmCriteria.size()).isGreaterThan(5);
        assertThat(service.identifyGaps(AUDIT_HISTORY_ID)).hasSize(5);
    }
}
