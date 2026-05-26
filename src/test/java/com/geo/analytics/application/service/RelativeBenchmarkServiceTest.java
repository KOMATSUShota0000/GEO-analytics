package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.enums.SubscriptionPlan;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository;
import com.geo.analytics.infrastructure.repository.AuditRubricResultRepository.RubricBenchmarkAggregate;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.web.dto.AnalyticsSummaryResponse;
import com.geo.analytics.web.dto.CompetitorSharePoint;
import com.geo.analytics.web.dto.RelativeBenchmarkResponse;
import com.geo.analytics.web.dto.TrendDataPoint;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RelativeBenchmarkServiceTest {

    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Mock private AnalyticsAggregationService analyticsAggregationService;
    @Mock private JobRepository jobRepository;
    @Mock private AuditHistoryRepository auditHistoryRepository;
    @Mock private AuditRubricResultRepository auditRubricResultRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private RelativeBenchmarkService service() {
        return new RelativeBenchmarkService(
                analyticsAggregationService,
                jobRepository,
                auditHistoryRepository,
                auditRubricResultRepository,
                jdbcTemplate);
    }

    private static final class Agg implements RubricBenchmarkAggregate {
        private final Boolean self;
        private final String criterion;
        private final Number avg;

        Agg(Boolean self, String criterion, Number avg) {
            this.self = self;
            this.criterion = criterion;
            this.avg = avg;
        }

        @Override
        public Boolean getSelfFlag() {
            return self;
        }

        @Override
        public String getCriterionId() {
            return criterion;
        }

        @Override
        public Number getAvgScore() {
            return avg;
        }
    }

    @Test
    void getBenchmark_whenProjectNotAnalyzed_returnsUnavailable() {
        when(analyticsAggregationService.summarizeProject(PROJECT_ID)).thenReturn(Optional.empty());

        RelativeBenchmarkResponse res = service().getBenchmark(PROJECT_ID);

        assertThat(res.locked()).isFalse();
        assertThat(res.available()).isFalse();
        assertThat(res.rows()).isEmpty();
    }

    @Test
    void getBenchmark_whenStandardPlan_returnsLocked() {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                List.of(), List.of(), SubscriptionPlan.STANDARD);
        when(analyticsAggregationService.summarizeProject(PROJECT_ID))
                .thenReturn(Optional.of(summary));

        RelativeBenchmarkResponse res = service().getBenchmark(PROJECT_ID);

        assertThat(res.locked()).isTrue();
        assertThat(res.available()).isFalse();
        assertThat(res.rows()).isEmpty();
    }

    @Test
    void assemble_withFullProData_buildsThreeRowsWithCorrectGaps() {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                List.of(
                        new TrendDataPoint(LocalDate.parse("2026-05-01"), 30.0, 50.0),
                        new TrendDataPoint(LocalDate.parse("2026-05-10"), 42.0, 55.0)),
                List.of(
                        new CompetitorSharePoint("A社", 58.0),
                        new CompetitorSharePoint("B社", 30.0)),
                SubscriptionPlan.PRO);
        // MACHINE_READABILITY_SIGNAL max 25: self 20 (0.8 良好) vs comp 10 (0.4 普通) -> gap false
        // ENTITY_BIOGRAPHY max 5: self 2 (0.4 普通) vs comp 4 (0.8 良好) -> gap true
        Map<String, double[]> rubric = Map.of(
                "MACHINE_READABILITY_SIGNAL", new double[] {20.0, 10.0},
                "ENTITY_BIOGRAPHY", new double[] {2.0, 4.0});

        RelativeBenchmarkResponse res = RelativeBenchmarkService.assemble(summary, rubric);

        assertThat(res.locked()).isFalse();
        assertThat(res.available()).isTrue();
        assertThat(res.rows()).hasSize(3);

        assertThat(res.rows().get(0).label()).isEqualTo("AI言及シェア");
        assertThat(res.rows().get(0).selfLabel()).isEqualTo("42.0%");
        assertThat(res.rows().get(0).competitorLabel()).isEqualTo("58.0%");
        assertThat(res.rows().get(0).gap()).isTrue();

        assertThat(res.rows().get(1).label()).isEqualTo("構造化シグナル密度");
        assertThat(res.rows().get(1).selfLabel()).isEqualTo("良好");
        assertThat(res.rows().get(1).competitorLabel()).isEqualTo("普通");
        assertThat(res.rows().get(1).gap()).isFalse();

        assertThat(res.rows().get(2).label()).isEqualTo("エンティティ解像度");
        assertThat(res.rows().get(2).selfLabel()).isEqualTo("普通");
        assertThat(res.rows().get(2).competitorLabel()).isEqualTo("良好");
        assertThat(res.rows().get(2).gap()).isTrue();
    }

    @Test
    void assemble_withNoSelfData_returnsUnavailableInsteadOfFakeValues() {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                List.of(), List.of(), SubscriptionPlan.PRO);

        RelativeBenchmarkResponse res = RelativeBenchmarkService.assemble(summary, Map.of());

        assertThat(res.available()).isFalse();
        assertThat(res.rows()).isEmpty();
    }

    @Test
    void assemble_withSelfSomButNoCompetitor_marksCompetitorAsNoData() {
        AnalyticsSummaryResponse summary = new AnalyticsSummaryResponse(
                List.of(new TrendDataPoint(LocalDate.parse("2026-05-10"), 42.0, 55.0)),
                List.of(),
                SubscriptionPlan.PRO);

        RelativeBenchmarkResponse res = RelativeBenchmarkService.assemble(summary, Map.of());

        assertThat(res.available()).isTrue();
        assertThat(res.rows().get(0).selfLabel()).isEqualTo("42.0%");
        assertThat(res.rows().get(0).competitorLabel()).isEqualTo("データなし");
        assertThat(res.rows().get(0).gap()).isFalse();
        assertThat(res.rows().get(1).selfLabel()).isEqualTo("データなし");
    }

    @Test
    void foldAggregates_groupsSelfAndCompetitorByCriterion() {
        List<RubricBenchmarkAggregate> aggregates = List.of(
                new Agg(Boolean.TRUE, "MACHINE_READABILITY_SIGNAL", 18.5),
                new Agg(Boolean.FALSE, "MACHINE_READABILITY_SIGNAL", 9.0),
                new Agg(Boolean.TRUE, "ENTITY_BIOGRAPHY", 3.0),
                new Agg(null, "ENTITY_BIOGRAPHY", 1.0));

        Map<String, double[]> folded = RelativeBenchmarkService.foldAggregates(aggregates);

        assertThat(folded.get("MACHINE_READABILITY_SIGNAL")).containsExactly(18.5, 9.0);
        // null selfFlag row is skipped; competitor stays NaN
        double[] entity = folded.get("ENTITY_BIOGRAPHY");
        assertThat(entity[0]).isEqualTo(3.0);
        assertThat(Double.isNaN(entity[1])).isTrue();
    }
}
