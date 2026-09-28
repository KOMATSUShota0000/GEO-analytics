package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 通知先メールアドレスの整え方（#174）。 */
class ProjectSettingsServiceNotificationEmailsTest {

    @Test
    void trimsAndDropsBlanks() {
        assertThat(ProjectSettingsService.normalizeNotificationEmails(Arrays.asList("  a@example.com ", "", "   ", null)))
                .containsExactly("a@example.com");
    }

    @Test
    void mergesAddressesThatDifferOnlyInCase_keepingTheFirstSpelling() {
        assertThat(ProjectSettingsService.normalizeNotificationEmails(List.of("Taro@Example.com", "taro@example.com", "b@example.com")))
                .containsExactly("Taro@Example.com", "b@example.com");
    }

    @Test
    void acceptsThree() {
        assertThat(ProjectSettingsService.normalizeNotificationEmails(List.of("a@example.com", "b@example.com", "c@example.com")))
                .hasSize(3);
    }

    @Test
    void countsAfterMergingDuplicates() {
        assertThat(ProjectSettingsService.normalizeNotificationEmails(
                        List.of("a@example.com", "b@example.com", "c@example.com", "A@example.com")))
                .containsExactly("a@example.com", "b@example.com", "c@example.com");
    }

    @Test
    void rejectsFour() {
        assertThatThrownBy(() -> ProjectSettingsService.normalizeNotificationEmails(
                        List.of("a@example.com", "b@example.com", "c@example.com", "d@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("通知先のメールアドレスは3件まで登録できます");
    }

    @Test
    void rejectsMalformedAddress_withoutEchoingIt() {
        assertThatThrownBy(() -> ProjectSettingsService.normalizeNotificationEmails(List.of("not-an-address")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("メールアドレスの形式になっていないものがあります");
    }

    @Test
    void rejectsAddressLongerThan320Characters() {
        String tooLong = "a".repeat(64) + "@" + ("b".repeat(60) + ".").repeat(5) + "com";
        assertThat(tooLong.length()).isGreaterThan(320);
        assertThatThrownBy(() -> ProjectSettingsService.normalizeNotificationEmails(List.of(tooLong)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyListClearsRecipients() {
        assertThat(ProjectSettingsService.normalizeNotificationEmails(List.of())).isEmpty();
    }
}
