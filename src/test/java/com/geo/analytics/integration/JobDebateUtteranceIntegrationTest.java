package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.BatchPersistenceService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.ai.DebatePersona;
import com.geo.analytics.domain.enums.DebateEvidenceKind;
import com.geo.analytics.domain.enums.DebateStance;
import com.geo.analytics.domain.enums.DebateStatus;
import com.geo.analytics.domain.model.DebateUtterance;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import jakarta.persistence.EntityManager;
import java.lang.ScopedValue;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #197: 議論の発言を裏の処理（batch_worker）で保存し、画面側（api_worker・RLS あり）から自社の分だけ読めることを確かめる。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class JobDebateUtteranceIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID ORG_B = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final UUID WORKSPACE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0197");
    private static final UUID PROJECT = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc0197");
    private static final UUID JOB = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddd0197");

    private static final DebateUtterance ANALYST_FIRST = new DebateUtterance(
            1, DebatePersona.ANALYST, "社名が出たのは2問だけでした。", null, null,
            DebateEvidenceKind.QUERY_MENTIONS, null, "社名が出た質問 2問 / 10問");
    private static final DebateUtterance SKEPTIC_FIRST = new DebateUtterance(
            1, DebatePersona.SKEPTIC, "3か月は長すぎます。", DebatePersona.INNOVATOR, DebateStance.REBUT,
            DebateEvidenceKind.REMEDIATION_TASK, 1, "");
    private static final DebateUtterance DIRECTOR_SUMMARY =
            DebateUtterance.summaryOnly(null, DebatePersona.DIRECTOR, "まとめます。");

    @Autowired
    private BatchPersistenceService batchPersistenceService;

    @Autowired
    @Qualifier("batchJdbcTemplate")
    private JdbcTemplate batchJdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @BeforeEach
    void seed() {
        // Why: batch_worker は BYPASSRLS のため、テナント文脈なしで行を用意できる
        batchJdbcTemplate.update("""
                INSERT INTO workspaces (id, name, subscription_plan, organization_id, created_at, updated_at)
                VALUES (?, 'Workspace 197', 'PRO', ?, now(), now())
                ON CONFLICT (id) DO NOTHING
                """, WORKSPACE, ORG_A);
        batchJdbcTemplate.update("""
                INSERT INTO projects (id, tenant_id, name, target_url, brand_color, created_at, updated_at, auto_audit_enabled)
                VALUES (?, ?, '議論の案件', 'https://example.com', '#4F46E5', now(), now(), false)
                ON CONFLICT (id) DO NOTHING
                """, PROJECT, WORKSPACE.toString());
        batchJdbcTemplate.update("""
                INSERT INTO jobs (id, tenant_id, project_id, job_status, brand_name, brand_color, target_url, industry_type,
                                  gap_analysis_completed, created_at, updated_at)
                VALUES (?, ?, ?, 'COMPLETED', '議論の案件', '#4F46E5', 'https://example.com', 'CORPORATE_SERVICE', true, now(), now())
                ON CONFLICT (id) DO NOTHING
                """, JOB, WORKSPACE.toString(), PROJECT);
        batchPersistenceService.beginDebate(JOB);
    }

    @Test
    void savedUtterancesAreReadableInOrderByTheOwningOrganizationOnly() {
        batchPersistenceService.insertDebateUtterance(JOB, ORG_A, 1, ANALYST_FIRST);
        batchPersistenceService.insertDebateUtterance(JOB, ORG_A, 2, SKEPTIC_FIRST);
        batchPersistenceService.insertDebateUtterance(JOB, ORG_A, 3, DIRECTOR_SUMMARY);

        List<Object[]> rows = utterancesAs(ORG_A);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsExactly(
                (short) 1, (short) 1, "ANALYST", "社名が出たのは2問だけでした。", null, null,
                "QUERY_MENTIONS", null, "社名が出た質問 2問 / 10問");
        assertThat(rows.get(1)).containsExactly(
                (short) 2, (short) 1, "SKEPTIC", "3か月は長すぎます。", "INNOVATOR", "REBUT",
                "REMEDIATION_TASK", (short) 1, "");
        assertThat(rows.get(2)).containsExactly(
                (short) 3, null, "DIRECTOR", "まとめます。", null, null, null, null, "");
        assertThat(utterancesAs(ORG_B)).isEmpty();
        assertThat(debateStatus()).isEqualTo("RUNNING");
    }

    @Test
    void apiWorkerCannotWriteUtterances() {
        assertThatThrownBy(() -> as(ORG_A, () -> entityManager
                .createNativeQuery("""
                        INSERT INTO job_debate_utterances (id, job_id, organization_id, seq, speaker, summary)
                        VALUES (gen_random_uuid(), :job, :org, 99, 'ANALYST', '書けないはず')
                        """)
                .setParameter("job", JOB)
                .setParameter("org", ORG_A)
                .executeUpdate()))
                .hasStackTraceContaining("permission denied");
    }

    /** やり直したときは前の発言を消して置き換え、失敗したときは途中までの発言を消す（2026-09-30 オーナー確定）。 */
    @Test
    void restartAndFailureRemoveEarlierUtterances() {
        batchPersistenceService.insertDebateUtterance(JOB, ORG_A, 1, ANALYST_FIRST);

        batchPersistenceService.beginDebate(JOB);
        assertThat(utterancesAs(ORG_A)).isEmpty();

        batchPersistenceService.insertDebateUtterance(JOB, ORG_A, 1, ANALYST_FIRST);
        batchPersistenceService.finishDebate(JOB, DebateStatus.FAILED);
        assertThat(utterancesAs(ORG_A)).isEmpty();
        assertThat(debateStatus()).isEqualTo("FAILED");
    }

    /** 議論のあとで裏の処理が落ちても、終わった議論の結果は消さない。議論中のまま落ちたら失敗にする。 */
    @Test
    void abandonOnlyTouchesDebatesStillRunning() {
        batchPersistenceService.insertDebateUtterance(JOB, ORG_A, 1, ANALYST_FIRST);
        batchPersistenceService.finishDebate(JOB, DebateStatus.COMPLETED);

        batchPersistenceService.abandonDebate(JOB);
        assertThat(debateStatus()).isEqualTo("COMPLETED");
        assertThat(utterancesAs(ORG_A)).hasSize(1);

        batchPersistenceService.beginDebate(JOB);
        batchPersistenceService.insertDebateUtterance(JOB, ORG_A, 1, ANALYST_FIRST);
        batchPersistenceService.abandonDebate(JOB);
        assertThat(debateStatus()).isEqualTo("FAILED");
        assertThat(utterancesAs(ORG_A)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> utterancesAs(UUID organizationId) {
        return as(organizationId, () -> entityManager
                .createNativeQuery("""
                        SELECT seq, round, speaker, summary, reply_to, stance, evidence_kind, evidence_task_number,
                               evidence_detail
                        FROM job_debate_utterances WHERE job_id = :job ORDER BY seq
                        """)
                .setParameter("job", JOB)
                .getResultList());
    }

    private String debateStatus() {
        return batchJdbcTemplate.queryForObject("SELECT debate_status FROM jobs WHERE id = ?", String.class, JOB);
    }

    private <T> T as(UUID organizationId, Supplier<T> work) {
        return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(organizationId, null, null))
                .call(() -> transactionTemplate.execute(status -> {
                    entityManager
                            .createNativeQuery("SELECT set_config('app.current_org_id', :org, true)")
                            .setParameter("org", organizationId.toString())
                            .getSingleResult();
                    return work.get();
                }));
    }
}
