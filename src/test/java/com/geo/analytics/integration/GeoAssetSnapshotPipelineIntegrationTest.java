package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.pipeline.GeoAssetSnapshotPipeline;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 「解析完了」の合図から資産スナップショットが1行保存されることを、RLS が効く接続（api_worker）で確かめる（#180）。
 * 以前はワークスペースを @Transactional なしで読んでいたため組織IDが渡らず、何も保存されていなかった。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class GeoAssetSnapshotPipelineIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID WORKSPACE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0180");
    private static final UUID PROJECT = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc0180");
    private static final UUID JOB = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddd0180");
    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    private GeoAssetSnapshotPipeline geoAssetSnapshotPipeline;

    @Autowired
    @Qualifier("batchJdbcTemplate")
    private JdbcTemplate batchJdbcTemplate;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @BeforeEach
    void seed() {
        // Why: batch_worker は BYPASSRLS のため、テナント文脈なしで行を用意できる
        batchJdbcTemplate.update("""
                INSERT INTO workspaces (id, name, subscription_plan, organization_id, created_at, updated_at)
                VALUES (?, 'Workspace 180', 'STANDARD', ?, now(), now())
                ON CONFLICT (id) DO NOTHING
                """, WORKSPACE, ORG_A);
        batchJdbcTemplate.update("""
                INSERT INTO projects (id, tenant_id, name, target_url, brand_color, created_at, updated_at, auto_audit_enabled)
                VALUES (?, ?, 'スナップショット案件', 'https://example.com', '#4F46E5', now(), now(), false)
                ON CONFLICT (id) DO NOTHING
                """, PROJECT, WORKSPACE.toString());
        batchJdbcTemplate.update("""
                INSERT INTO jobs (id, tenant_id, project_id, job_status, brand_name, brand_color, target_url, industry_type,
                                  gap_analysis_completed, created_at, updated_at)
                VALUES (?, ?, ?, 'COMPLETED', 'スナップショット案件', '#4F46E5', 'https://example.com', 'CORPORATE_SERVICE', true, now(), now())
                ON CONFLICT (id) DO NOTHING
                """, JOB, WORKSPACE.toString(), PROJECT);
        batchJdbcTemplate.update("DELETE FROM geo_asset_snapshots WHERE project_id = ?", PROJECT);
    }

    @Test
    void completedAuditLeavesOneSnapshot_withoutAnyTenantContext() throws InterruptedException {
        // Why: 解析完了の合図は、テナント文脈のない非同期の処理から出ることがあるため、文脈を張らずに呼ぶ
        geoAssetSnapshotPipeline.onProjectAuditCompleted(new ProjectAuditCompletedEvent(PROJECT, JOB, WORKSPACE));

        assertThat(awaitSnapshotCount(1)).isEqualTo(1);
        UUID organizationId = batchJdbcTemplate.queryForObject(
                "SELECT organization_id FROM geo_asset_snapshots WHERE project_id = ?", UUID.class, PROJECT);
        assertThat(organizationId).isEqualTo(ORG_A);
    }

    // Why: スナップショットは合図を受けたあと別の仮想スレッドで保存されるため、行ができるまで待つ
    private int awaitSnapshotCount(int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        int count = 0;
        while (Instant.now().isBefore(deadline)) {
            Integer found = batchJdbcTemplate.queryForObject(
                    "SELECT count(*) FROM geo_asset_snapshots WHERE project_id = ?", Integer.class, PROJECT);
            count = found != null ? found : 0;
            if (count >= expected) {
                return count;
            }
            Thread.sleep(100);
        }
        return count;
    }
}
