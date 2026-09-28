package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.NotificationService;
import com.geo.analytics.application.service.ProjectSettingsService;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.web.dto.ProjectSettingsPatchRequest;
import com.geo.analytics.web.dto.ProjectSettingsResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 通知設定の読み書きと解析完了のお知らせを、RLS が効く接続（api_worker）と Mailpit で確かめる（#174）。
 * 以前は @Transactional を通らずにリポジトリを呼んでいたため組織IDが渡らず、設定の読み込みもお知らせの送信も失敗していた。
 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rls-it")
class ProjectNotificationIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID WORKSPACE = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0174");
    private static final UUID PROJECT = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc0174");
    private static final Duration MAIL_TIMEOUT = Duration.ofSeconds(15);

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        MailpitTestSupport.registerMailProperties(registry);
    }

    @Autowired
    private ProjectSettingsService projectSettingsService;

    @Autowired
    private NotificationService notificationService;

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
                VALUES (?, 'Workspace 174', 'STANDARD', ?, now(), now())
                ON CONFLICT (id) DO NOTHING
                """, WORKSPACE, ORG_A);
        batchJdbcTemplate.update("""
                INSERT INTO projects (id, tenant_id, name, target_url, brand_color, created_at, updated_at, auto_audit_enabled)
                VALUES (?, ?, '通知テスト案件', 'https://example.com', '#4F46E5', now(), now(), false)
                ON CONFLICT (id) DO UPDATE SET notification_emails = '{}'
                """, PROJECT, WORKSPACE.toString());
        MailpitTestSupport.deleteAll();
    }

    @Test
    void savedRecipientsCanBeReadBack() {
        asOrgA(() -> projectSettingsService.patch(PROJECT, new ProjectSettingsPatchRequest(null, List.of(" Owner@Example.com ", "owner@example.com", "team@example.com"))));

        Optional<ProjectSettingsResponse> settings = asOrgA(() -> projectSettingsService.getSettings(PROJECT));

        assertThat(settings).isPresent();
        assertThat(settings.get().notificationEmails()).containsExactly("Owner@Example.com", "team@example.com");
    }

    @Test
    void omittedRecipientsAreLeftUnchanged() {
        asOrgA(() -> projectSettingsService.patch(PROJECT, new ProjectSettingsPatchRequest(null, List.of("a@example.com"))));

        ProjectSettingsResponse response = asOrgA(() -> projectSettingsService.patch(PROJECT, new ProjectSettingsPatchRequest(true, null)));

        assertThat(response.notificationEmails()).containsExactly("a@example.com");
        assertThat(response.autoAuditEnabled()).isTrue();
    }

    @Test
    void fourRecipientsAreRejectedAndNothingIsSaved() {
        asOrgA(() -> projectSettingsService.patch(PROJECT, new ProjectSettingsPatchRequest(null, List.of("a@example.com"))));

        assertThatThrownBy(() -> asOrgA(() -> projectSettingsService.patch(PROJECT, new ProjectSettingsPatchRequest(
                        null, List.of("a@example.com", "b@example.com", "c@example.com", "d@example.com")))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(asOrgA(() -> projectSettingsService.getSettings(PROJECT)).orElseThrow().notificationEmails())
                .containsExactly("a@example.com");
    }

    @Test
    void databaseAlsoRefusesMoreThanThreeRecipients() {
        assertThatThrownBy(() -> batchJdbcTemplate.update(
                        "UPDATE projects SET notification_emails = ARRAY['a@x.jp','b@x.jp','c@x.jp','d@x.jp'] WHERE id = ?", PROJECT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void completedAuditIsMailedToEachRecipient_withoutAnyTenantContext() {
        batchJdbcTemplate.update(
                "UPDATE projects SET notification_emails = ARRAY['owner@example.com','team@example.com'] WHERE id = ?", PROJECT);

        // Why: 非同期のリスナーから呼ばれる場面を再現するため、テナント文脈を張らずに呼ぶ
        notificationService.deliver(new ProjectAuditCompletedEvent(PROJECT, UUID.randomUUID(), WORKSPACE));

        List<MailpitTestSupport.Message> toOwner = MailpitTestSupport.awaitMessagesTo("owner@example.com", 1, MAIL_TIMEOUT);
        List<MailpitTestSupport.Message> toTeam = MailpitTestSupport.awaitMessagesTo("team@example.com", 1, MAIL_TIMEOUT);
        assertThat(toOwner).singleElement().satisfies(m -> {
            assertThat(m.subject()).contains("通知テスト案件");
            assertThat(m.to()).doesNotContain("team@example.com");
        });
        assertThat(toTeam).hasSize(1);
    }

    @Test
    void nothingIsMailedWithoutRecipients() {
        notificationService.deliver(new ProjectAuditCompletedEvent(PROJECT, UUID.randomUUID(), WORKSPACE));

        assertThat(MailpitTestSupport.totalMessages()).isZero();
    }

    private static <T> T asOrgA(Supplier<T> action) {
        return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(ORG_A, WORKSPACE, null)).call(action::get);
    }
}
