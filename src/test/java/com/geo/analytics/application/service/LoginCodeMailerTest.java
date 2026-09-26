package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginCodeMailerTest {

    @Test
    void body_isTheAgreedText_withCodeAndJapanTimeDeadline() {
        // 2026-09-26T06:42:00Z は日本時間 15:42
        String body = LoginCodeMailer.body("012345", Instant.parse("2026-09-26T06:42:00Z"), Duration.ofMinutes(10));

        assertThat(body).isEqualTo("""
                GEOアナリティクスのログインコードをお送りします。

                    012345

                ログイン画面にこの6桁の数字を入力してください。
                コードの有効期限は10分です（15:42まで）。

                このコードは他の人に教えないでください。
                このメールに心当たりがない場合は、何もせず削除してください。
                他の方がメールアドレスを打ち間違えた可能性があります。
                """);
    }

    @Test
    void deadlineHour_hasNoLeadingZero() {
        String body = LoginCodeMailer.body("999999", Instant.parse("2026-09-26T00:05:00Z"), Duration.ofMinutes(10));

        assertThat(body).contains("（9:05まで）");
    }

    @Test
    void subject_doesNotCarryTheCode() {
        assertThat(LoginCodeMailer.SUBJECT).isEqualTo("GEOアナリティクスのログインコード").doesNotContainPattern("\\d");
    }
}
