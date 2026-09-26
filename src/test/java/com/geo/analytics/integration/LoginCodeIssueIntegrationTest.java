package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.entity.LoginCode;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.repository.LoginCodeRepository;
import com.geo.analytics.infrastructure.security.LoginCodeHasher;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.integration.MailpitTestSupport.Message;
import java.lang.ScopedValue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

/** ログインコードの発行（#146）を、実際の PostgreSQL（RLS 有効）と Mailpit で確かめる。 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rls-it")
class LoginCodeIssueIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
    private static final UUID ORG_B = UUID.fromString("22222222-2222-2222-2222-222222222202");
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01");
    private static final String ADMIN_EMAIL = "org-a-admin@test.local";
    private static final Pattern CODE = Pattern.compile("(?m)^\\s+(\\d{6})\\s*$");
    private static final Duration MAIL_TIMEOUT = Duration.ofSeconds(15);

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        MailpitTestSupport.registerMailProperties(registry);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LoginCodeRepository loginCodeRepository;

    @Autowired
    private LoginCodeHasher loginCodeHasher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private GeoCompetitorSearchAdapter geoCompetitorSearchAdapter;

    @MockitoBean
    private SyncVerificationService syncVerificationService;

    @BeforeEach
    void clearMailbox() {
        MailpitTestSupport.deleteAll();
    }

    @Test
    void registeredAddress_receivesSixDigitCode_andOnlyItsHashIsStored() {
        Instant before = Instant.now();
        requestCode(ADMIN_EMAIL).expectStatus().isAccepted();

        Message mail = MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, 1, MAIL_TIMEOUT).getFirst();
        String code = codeIn(mail);
        assertThat(mail.subject()).isEqualTo("GEOアナリティクスのログインコード").doesNotContain(code);
        assertThat(mail.text()).contains("有効期限は10分").contains("他の人に教えないでください");

        LoginCode row = rowAs(ORG_A).orElseThrow();
        assertThat(row.getOrganizationId()).isEqualTo(ORG_A);
        assertThat(row.getCodeHash()).isEqualTo(loginCodeHasher.hash(ADMIN_ID, code)).doesNotContain(code);
        assertThat(row.getIssuedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(Duration.between(row.getIssuedAt(), row.getExpiresAt())).isEqualTo(Duration.ofMinutes(10));
        assertThat(row.getFailedAttempts()).isZero();
        assertThat(row.getConsumedAt()).isNull();
    }

    @Test
    void addressIsMatchedIgnoringCaseAndSurroundingSpaces() {
        requestCode("  ORG-A-Admin@Test.Local ").expectStatus().isAccepted();

        assertThat(MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, 1, MAIL_TIMEOUT)).hasSize(1);
    }

    @Test
    void unregisteredAddress_getsTheSameResponse_andNoMail() {
        EntityExchangeResult<byte[]> unregistered =
                requestCode("nobody@test.local").expectStatus().isAccepted().expectBody().returnResult();
        EntityExchangeResult<byte[]> registered =
                requestCode(ADMIN_EMAIL).expectStatus().isAccepted().expectBody().returnResult();

        assertThat(unregistered.getResponseBody()).isEqualTo(registered.getResponseBody());
        // 登録済みのメールが届いた時点で、先に要求した未登録の処理（検索のみ）は終わっている
        MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, 1, MAIL_TIMEOUT);
        assertThat(MailpitTestSupport.messagesTo("nobody@test.local")).isEmpty();
        assertThat(MailpitTestSupport.totalMessages()).isEqualTo(1);
    }

    @Test
    void reissue_overwritesTheOnlyRow_andResetsFailuresAndConsumption() {
        requestCode(ADMIN_EMAIL).expectStatus().isAccepted();
        String first = codeIn(MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, 1, MAIL_TIMEOUT).getFirst());
        markUsedAndFailed();

        requestCode(ADMIN_EMAIL).expectStatus().isAccepted();
        List<Message> mails = MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, 2, MAIL_TIMEOUT);
        String second = mails.stream().map(LoginCodeIssueIntegrationTest::codeIn)
                .filter(c -> !c.equals(first)).findFirst().orElse(first);
        assumeThat(second).as("2回のコードが偶然一致した（100万分の1）").isNotEqualTo(first);

        LoginCode row = rowAs(ORG_A).orElseThrow();
        assertThat(row.getCodeHash()).isEqualTo(loginCodeHasher.hash(ADMIN_ID, second))
                .isNotEqualTo(loginCodeHasher.hash(ADMIN_ID, first));
        assertThat(row.getFailedAttempts()).isZero();
        assertThat(row.getConsumedAt()).isNull();
        assertThat(inTransactionAs(ORG_A, () -> loginCodeRepository.count())).isEqualTo(1);
    }

    @Test
    void codeRow_isInvisibleFromAnotherOrganization() {
        requestCode(ADMIN_EMAIL).expectStatus().isAccepted();
        MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, 1, MAIL_TIMEOUT);

        assertThat(rowAs(ORG_A)).isPresent();
        assertThat(rowAs(ORG_B)).isEmpty();
    }

    @Test
    void malformedAddress_isRejected() {
        requestCode("not-an-email").expectStatus().isBadRequest();
        requestCode("   ").expectStatus().isBadRequest();
    }

    private WebTestClient.ResponseSpec requestCode(String email) {
        return webTestClient
                .post()
                .uri("/api/auth/code")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email))
                .exchange();
    }

    private Optional<LoginCode> rowAs(UUID organizationId) {
        return inTransactionAs(organizationId, () -> loginCodeRepository.findById(ADMIN_ID));
    }

    // RLS の組織IDは RlsConnectionInterceptor が @Transactional のサービス呼び出しで接続へ渡す。
    // テストからリポジトリを直接読むときはその経路を通らないため、同じ設定をトランザクション内で行ってから読む。
    private <T> T inTransactionAs(UUID organizationId, Supplier<T> work) {
        return ScopedValue.where(TenantContextHolder.CONTEXT, new TenantIdentity(organizationId, null, null))
                .call(() -> transactionTemplate.execute(status -> {
                    entityManager
                            .createNativeQuery("SELECT set_config('app.current_org_id', :org, true)")
                            .setParameter("org", organizationId.toString())
                            .getSingleResult();
                    return work.get();
                }));
    }

    private void markUsedAndFailed() {
        inTransactionAs(ORG_A, () -> {
            LoginCode row = loginCodeRepository.findById(ADMIN_ID).orElseThrow();
            row.setFailedAttempts(3);
            row.setConsumedAt(Instant.now());
            return loginCodeRepository.saveAndFlush(row);
        });
        LoginCode updated = rowAs(ORG_A).orElseThrow();
        assertThat(updated.getFailedAttempts()).isEqualTo(3);
        assertThat(updated.getConsumedAt()).isNotNull();
    }

    private static String codeIn(Message mail) {
        Matcher m = CODE.matcher(mail.text());
        assertThat(m.find()).as("本文に6桁のコードがある: %s", mail.text()).isTrue();
        return m.group(1);
    }
}
