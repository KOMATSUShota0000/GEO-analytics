package com.geo.analytics.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geo.analytics.application.dto.StrategyInsight;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.DebateEvidenceKind;
import com.geo.analytics.domain.enums.DebateStance;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.model.DebateUtterance;
import java.time.LocalDateTime;
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

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 30, 12, 0);
    private static final List<DebateUtterance> UTTERANCES = List.of(
            DebateUtterance.summaryOnly(1, DebatePersona.ANALYST, "社名が出たのは2問だけでした。"),
            new DebateUtterance(1, DebatePersona.SKEPTIC, "3か月は長すぎます。", DebatePersona.INNOVATOR,
                    DebateStance.REBUT, DebateEvidenceKind.REMEDIATION_TASK, 1, ""));

    private static JobEntity debatingJob(String debateStatus, LocalDateTime updatedAt) {
        JobEntity job = job(null);
        job.setDebateStatus(debateStatus);
        job.setUpdatedAt(updatedAt);
        return job;
    }

    /** #198: 議論中は、状態と、そこまでの発言を話した順に返す。画面はこれを見て問い合わせを続ける。 */
    @Test
    void 議論中は状態とそこまでの発言を返す() {
        JobStatusResponse res =
                JobStatusResponse.from(debatingJob("RUNNING", NOW.minusMinutes(1)), TEMPLATE, UTTERANCES, NOW);

        assertThat(res.debateStatus()).isEqualTo("RUNNING");
        assertThat(res.debateUtterances()).extracting(DebateUtteranceResponse::speaker)
                .containsExactly("ANALYST", "SKEPTIC");
        assertThat(res.debateUtterances().get(1).replyTo()).isEqualTo("INNOVATOR");
        assertThat(res.debateUtterances().get(1).stance()).isEqualTo("REBUT");
        assertThat(res.debateUtterances().get(1).evidenceTaskNumber()).isEqualTo(1);
    }

    /** #198: サーバーが議論の途中で止まっても、画面が問い合わせを続けないようにする。 */
    @Test
    void 長く議論中のままなら失敗として返し_発言は返さない() {
        LocalDateTime justOver = NOW.minus(JobStatusResponse.DEBATE_STALE_AFTER).minusSeconds(1);
        LocalDateTime justWithin = NOW.minus(JobStatusResponse.DEBATE_STALE_AFTER).plusSeconds(1);

        JobStatusResponse stale = JobStatusResponse.from(debatingJob("RUNNING", justOver), TEMPLATE, UTTERANCES, NOW);
        JobStatusResponse fresh = JobStatusResponse.from(debatingJob("RUNNING", justWithin), TEMPLATE, UTTERANCES, NOW);

        assertThat(stale.debateStatus()).isEqualTo("FAILED");
        assertThat(stale.debateUtterances()).isEmpty();
        assertThat(fresh.debateStatus()).isEqualTo("RUNNING");
        assertThat(fresh.debateUtterances()).hasSize(2);
    }

    @Test
    void 終わった議論は時間がたっても状態と発言をそのまま返す() {
        JobStatusResponse res =
                JobStatusResponse.from(debatingJob("COMPLETED", NOW.minusDays(30)), TEMPLATE, UTTERANCES, NOW);

        assertThat(res.debateStatus()).isEqualTo("COMPLETED");
        assertThat(res.debateUtterances()).hasSize(2);
    }

    /** 議論の状態を持つ前の解析と、発言を渡さない呼び出し（ジョブ作成の返事など）。 */
    @Test
    void 状態の無い解析は空の発言を返す() {
        JobStatusResponse res = JobStatusResponse.from(job("議論の診断"), TEMPLATE);

        assertThat(res.debateStatus()).isNull();
        assertThat(res.debateUtterances()).isEmpty();
    }

    /** 画面は snake_case の項目名で読む（R-27）。 */
    @Test
    void 議論の項目は_snake_case_で出力する() throws Exception {
        JobStatusResponse res =
                JobStatusResponse.from(debatingJob("RUNNING", NOW.minusMinutes(1)), TEMPLATE, UTTERANCES, NOW);

        JsonNode json = new ObjectMapper().findAndRegisterModules().valueToTree(res);

        assertThat(json.get("debate_status").asText()).isEqualTo("RUNNING");
        JsonNode second = json.get("debate_utterances").get(1);
        assertThat(second.get("reply_to").asText()).isEqualTo("INNOVATOR");
        assertThat(second.get("evidence_kind").asText()).isEqualTo("REMEDIATION_TASK");
        assertThat(second.get("evidence_task_number").asInt()).isEqualTo(1);
        assertThat(second.get("evidence_detail").asText()).isEmpty();
    }
}
