package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.application.service.SyncVerificationService;
import com.geo.analytics.domain.entity.LoginCode;
import com.geo.analytics.infrastructure.api.GeoCompetitorSearchAdapter;
import com.geo.analytics.infrastructure.repository.LoginCodeRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import com.geo.analytics.integration.MailpitTestSupport.Message;
import jakarta.persistence.EntityManager;
import java.lang.ScopedValue;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.support.TransactionTemplate;

/** ログインコードの照合（#151）を、実際の PostgreSQL（RLS 有効）と Mailpit で確かめる。 */
@SpringBootTest(classes = GeoAnalyticsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("rls-it")
class LoginCodeVerifyIntegrationTest extends PostgresTestBase {

    private static final UUID ORG_A = UUID.fromString("11111111-1111-1111-1111-111111111101");
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
    void correctCode_logsIn_andTheCodeIsMarkedUsed() {
        String code = issueAndReadCode(1);

        verify(ADMIN_EMAIL, code)
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.SET_COOKIE, v -> assertThat(v).containsIgnoringCase("refresh_token"))
                .expectBody().jsonPath("$.accessToken").isNotEmpty();
        assertThat(row().getConsumedAt()).isNotNull();
    }

    @Test
    void fourMistakesThenTheCorrectCode_stillLogsIn() {
        String code = issueAndReadCode(1);
        for (int i = 0; i < 4; i++) {
            verify(ADMIN_EMAIL, wrongFor(code)).expectStatus().isUnauthorized();
        }

        verify(ADMIN_EMAIL, code).expectStatus().isOk();
    }

    @Test
    void fiveMistakes_blockEvenTheCorrectCode() {
        String code = issueAndReadCode(1);
        for (int i = 0; i < 5; i++) {
            verify(ADMIN_EMAIL, wrongFor(code)).expectStatus().isUnauthorized();
        }

        verify(ADMIN_EMAIL, code).expectStatus().isUnauthorized();
        assertThat(row().getFailedAttempts()).isGreaterThanOrEqualTo(5);
        assertThat(row().getConsumedAt()).isNull();
    }

    @Test
    void expiredCode_isRejected() {
        String code = issueAndReadCode(1);
        inTransactionAs(ORG_A, () -> entityManager
                .createNativeQuery("UPDATE login_codes SET issued_at = now() - interval '20 minutes', "
                        + "expires_at = now() - interval '10 minutes' WHERE user_id = :userId")
                .setParameter("userId", ADMIN_ID)
                .executeUpdate());

        verify(ADMIN_EMAIL, code).expectStatus().isUnauthorized();
    }

    @Test
    void usedCode_cannotBeUsedAgain() {
        String code = issueAndReadCode(1);
        verify(ADMIN_EMAIL, code).expectStatus().isOk();

        verify(ADMIN_EMAIL, code).expectStatus().isUnauthorized();
    }

    @Test
    void oldCode_isRejectedAfterANewOneIsIssued() {
        String first = issueAndReadCode(1);
        String second = issueAndReadCode(2);
        assumeThat(second).as("2回のコードが偶然一致した（100万分の1）").isNotEqualTo(first);

        verify(ADMIN_EMAIL, first).expectStatus().isUnauthorized();
        verify(ADMIN_EMAIL, second).expectStatus().isOk();
    }

    @Test
    void everyRejection_looksTheSame_includingUnregisteredAddresses() {
        String code = issueAndReadCode(1);
        String wrong = rejectionBody(ADMIN_EMAIL, wrongFor(code));
        String unregistered = rejectionBody("nobody@test.local", code);
        verify(ADMIN_EMAIL, code).expectStatus().isOk();
        String used = rejectionBody(ADMIN_EMAIL, code);

        assertThat(wrong).contains("login_code_rejected").contains("コードが正しくないか、期限が切れています。");
        assertThat(unregistered).isEqualTo(wrong);
        assertThat(used).isEqualTo(wrong);
    }

    @Test
    void sameCodeSentTwiceAtOnce_logsInOnlyOnce() throws InterruptedException {
        String code = issueAndReadCode(1);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<HttpStatusCode> statuses = new ConcurrentLinkedQueue<>();
        List<Thread> threads = List.of(
                Thread.ofVirtual().name("verify-race-1").start(() -> race(start, code, statuses)),
                Thread.ofVirtual().name("verify-race-2").start(() -> race(start, code, statuses)));
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }

        assertThat(statuses).hasSize(2);
        assertThat(statuses.stream().filter(HttpStatusCode::is2xxSuccessful).count()).isEqualTo(1);
    }

    @Test
    void fullWidthDigitsAndSurroundingSpaces_areAccepted() {
        String code = issueAndReadCode(1);
        StringBuilder fullWidth = new StringBuilder(" ");
        code.chars().forEach(c -> fullWidth.append((char) (c - '0' + '０')));

        verify(" ORG-A-Admin@Test.Local ", fullWidth.append(' ').toString()).expectStatus().isOk();
    }

    @Test
    void malformedCode_isRejectedAsBadRequest() {
        verify(ADMIN_EMAIL, "12345").expectStatus().isBadRequest();
        verify(ADMIN_EMAIL, "abcdef").expectStatus().isBadRequest();
        verify(ADMIN_EMAIL, "").expectStatus().isBadRequest();
    }

    private void race(CountDownLatch start, String code, ConcurrentLinkedQueue<HttpStatusCode> statuses) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        statuses.add(verify(ADMIN_EMAIL, code).returnResult(String.class).getStatus());
    }

    private String issueAndReadCode(int expectedMailCount) {
        webTestClient.post().uri("/api/auth/code").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", ADMIN_EMAIL)).exchange().expectStatus().isAccepted();
        List<Message> mails = MailpitTestSupport.awaitMessagesTo(ADMIN_EMAIL, expectedMailCount, MAIL_TIMEOUT);
        Message newest = mails.getFirst();
        Matcher m = CODE.matcher(newest.text());
        assertThat(m.find()).as("本文に6桁のコードがある: %s", newest.text()).isTrue();
        return m.group(1);
    }

    private WebTestClient.ResponseSpec verify(String email, String code) {
        return webTestClient
                .post()
                .uri("/api/auth/code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("email", email, "code", code))
                .exchange();
    }

    private String rejectionBody(String email, String code) {
        byte[] body = verify(email, code).expectStatus().isUnauthorized().expectBody().returnResult().getResponseBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    private static String wrongFor(String code) {
        return "%06d".formatted((Integer.parseInt(code) + 1) % 1_000_000);
    }

    private LoginCode row() {
        return inTransactionAs(ORG_A, () -> loginCodeRepository.findById(ADMIN_ID)).orElseThrow();
    }

    // RLS の組織IDは RlsConnectionInterceptor が @Transactional のサービス呼び出しで接続へ渡す。
    // テストからリポジトリを直接触るときはその経路を通らないため、同じ設定をトランザクション内で行ってから触る。
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
}
