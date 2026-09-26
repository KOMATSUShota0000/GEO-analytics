package com.geo.analytics.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * #141: 旧実装は状態確認のたびにテンプレートから作り直した診断文を返し、画面がそれを優先表示していた。
 * 4ペルソナ議論の診断が画面に出ず、PDF（保存済みの診断）とも食い違っていた。
 */
class JobStatusResponseTest {

    private static JobEntity job(String storedDiagnostic) {
        JobEntity job = new JobEntity();
        job.setId(UUID.randomUUID());
        job.setJobStatus(JobStatus.COMPLETED);
        job.setJobDiagnosticMessage(storedDiagnostic);
        return job;
    }

    private static final StrategyInsight TEMPLATE = new StrategyInsight("テンプレートの診断", List.of("a"), 0.4);

    @Test
    void 保存済みの総合診断をテンプレートより優先する() {
        JobStatusResponse res = JobStatusResponse.from(job("議論の診断"), TEMPLATE);

        assertThat(res.diagnosticMessage()).isEqualTo("議論の診断");
        assertThat(res.jobMedianModifiedZ()).isEqualTo(0.4);
    }

    @Test
    void 保存前はテンプレートで埋める() {
        assertThat(JobStatusResponse.from(job(null), TEMPLATE).diagnosticMessage()).isEqualTo("テンプレートの診断");
        assertThat(JobStatusResponse.from(job("  "), TEMPLATE).diagnosticMessage()).isEqualTo("テンプレートの診断");
    }
}
