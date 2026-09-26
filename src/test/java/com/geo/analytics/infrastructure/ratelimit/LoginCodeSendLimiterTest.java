package com.geo.analytics.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.ratelimit.LoginCodeSendLimiter.Decision;
import com.geo.analytics.infrastructure.ratelimit.LoginCodeSendLimiter.Rejection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginCodeSendLimiterTest {

    private static final String ME = "me@example.com";
    private static final String OFFICE = "203.0.113.10";

    private final MovableClock clock = new MovableClock(Instant.parse("2026-09-26T00:00:00Z"));
    private final LoginCodeSendLimiter limiter = new LoginCodeSendLimiter(new AppProperties(), clock);

    @Test
    void resendWithinSixtySeconds_isTooSoon_thenAllowedAfterSixty() {
        assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).isTrue();

        clock.advance(Duration.ofSeconds(59));
        Decision tooSoon = limiter.tryAcquire(ME, OFFICE);
        assertThat(tooSoon.rejection()).isEqualTo(Rejection.RESEND_TOO_SOON);
        assertThat(tooSoon.retryAfter()).isEqualTo(Duration.ofSeconds(1));

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).isTrue();
    }

    @Test
    void sixthSendWithinAnyHour_isLimited_untilTheFirstLeavesTheHour() {
        sendEvery(Duration.ofSeconds(60), 5);

        clock.advance(Duration.ofSeconds(60));
        Decision sixth = limiter.tryAcquire(ME, OFFICE);
        assertThat(sixth.rejection()).isEqualTo(Rejection.SEND_LIMIT);
        // 最初の送信は5分前。窓から外れるのは55分後
        assertThat(sixth.retryAfter()).isEqualTo(Duration.ofMinutes(55));

        clock.advance(Duration.ofMinutes(55).minusSeconds(1));
        assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).isTrue();
    }

    @Test
    void eleventhSendWithinAnyDay_isLimited_evenIfSpreadAcrossHours() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire(ME, "198.51.100." + i).allowed()).as("send %d", i + 1).isTrue();
            clock.advance(Duration.ofMinutes(61));
        }

        Decision eleventh = limiter.tryAcquire(ME, "198.51.100.99");
        assertThat(eleventh.rejection()).isEqualTo(Rejection.SEND_LIMIT);
        assertThat(eleventh.retryAfter()).isEqualTo(Duration.ofDays(1).minus(Duration.ofMinutes(61 * 10)));
    }

    @Test
    void twentyFirstSendFromTheSameClientWithinAnHour_isLimited_acrossAddresses() {
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("user" + i + "@example.com", OFFICE).allowed()).isTrue();
        }

        assertThat(limiter.tryAcquire("user20@example.com", OFFICE).rejection()).isEqualTo(Rejection.SEND_LIMIT);
        assertThat(limiter.tryAcquire("user20@example.com", "203.0.113.11").allowed()).isTrue();
    }

    @Test
    void addressesDifferingOnlyInCaseOrSpaces_shareTheLimit() {
        assertThat(limiter.tryAcquire("Me@Example.com", OFFICE).allowed()).isTrue();

        assertThat(limiter.tryAcquire("  me@example.COM ", "203.0.113.99").rejection())
                .isEqualTo(Rejection.RESEND_TOO_SOON);
    }

    @Test
    void rejectedRequests_doNotCount() {
        assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).isTrue();
        for (int i = 0; i < 30; i++) {
            clock.advance(Duration.ofSeconds(1));
            assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).isFalse();
        }

        clock.advance(Duration.ofSeconds(30));
        assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).as("断られた30回は数えない").isTrue();
    }

    @Test
    void sendLimit_waitsAtLeastUntilTheResendIntervalPasses() {
        sendEvery(Duration.ofSeconds(60), 4);
        clock.advance(Duration.ofSeconds(60));
        assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).isTrue();

        clock.advance(Duration.ofSeconds(10));
        Decision decision = limiter.tryAcquire(ME, OFFICE);
        assertThat(decision.rejection()).isEqualTo(Rejection.SEND_LIMIT);
        assertThat(decision.retryAfter()).isGreaterThanOrEqualTo(Duration.ofSeconds(50));
    }

    private void sendEvery(Duration gap, int times) {
        for (int i = 0; i < times; i++) {
            if (i > 0) {
                clock.advance(gap);
            }
            assertThat(limiter.tryAcquire(ME, OFFICE).allowed()).as("send %d", i + 1).isTrue();
        }
    }

    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
