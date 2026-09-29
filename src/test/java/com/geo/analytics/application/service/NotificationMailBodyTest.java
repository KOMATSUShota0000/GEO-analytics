package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.application.service.ProjectAuditNoticeReader.AuditDigest;
import com.geo.analytics.application.service.ProjectAuditNoticeReader.VarianceLine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 解析完了のお知らせメールの文面（#183 オーナー確定）。 */
class NotificationMailBodyTest {

    private static final String URL = "https://app.example.com/job/11111111-1111-1111-1111-111111111111";

    @Test
    void subjectSaysTheAnalysisIsComplete() {
        assertThat(NotificationService.subject("サイボウズ")).isEqualTo("[GEOアナリティクス] サイボウズ の解析が完了しました");
    }

    @Test
    void bodyFollowsTheAgreedWording() {
        AuditDigest digest = new AuditDigest(
                44.6, 38.9, 5.7, List.of(new VarianceLine("サイボウズ cybozu.co.jp", 63.9, 68.6, -4.8)), 1, 2);

        String body = NotificationService.htmlBody("サイボウズ", digest, URL);

        assertThat(body)
                .contains("サイボウズ の解析が完了しました。")
                .contains("■ AIの回答に取り上げられた割合（SoMスコア）の平均")
                .contains("今回 44.6（前回 38.9 から +5.7）")
                .contains("■ 前回から大きく動いた質問")
                .contains("<li>サイボウズ cybozu.co.jp — 今回 63.9 / 前回 68.6 / Δ-4.8</li>")
                .contains("※ 前回と同じ質問は1件でした。今回はじめて測った質問が2件あります。")
                .contains("<a href='" + URL + "'>解析結果を見る</a>")
                .contains("このメールは、プロジェクト設定で登録された宛先にお送りしています。")
                .doesNotContain("監査")
                .doesNotContain("変動TOP3");
    }

    @Test
    void decreasedAverageHasNoPlusSign() {
        AuditDigest digest = new AuditDigest(35.7, 38.9, -3.2, List.of(), 0, 3);

        assertThat(NotificationService.htmlBody("案件", digest, null)).contains("今回 35.7（前回 38.9 から -3.2）");
    }

    @Test
    void firstAnalysisSaysThereIsNothingToCompare() {
        AuditDigest digest = new AuditDigest(44.6, null, null, List.of(), 0, 3);

        String body = NotificationService.htmlBody("案件", digest, null);

        assertThat(body)
                .contains("<p>今回 44.6</p>")
                .contains("前回の解析がないため、比べられる質問はありません。")
                .doesNotContain("※");
    }

    @Test
    void noSharedQuestionsIsExplained() {
        AuditDigest digest = new AuditDigest(44.6, 38.9, 5.7, List.of(), 0, 3);

        assertThat(NotificationService.htmlBody("案件", digest, null))
                .contains("前回と同じ質問がなかったため、比べられる質問はありません。")
                .contains("※ 前回と同じ質問は0件でした。今回はじめて測った質問が3件あります。");
    }

    @Test
    void newQuestionSentenceIsOmittedWhenThereAreNone() {
        AuditDigest digest = new AuditDigest(
                44.6, 38.9, 5.7, List.of(new VarianceLine("質問", 50.0, 40.0, 10.0)), 5, 0);

        assertThat(NotificationService.htmlBody("案件", digest, null))
                .contains("※ 前回と同じ質問は5件でした。</p>")
                .doesNotContain("今回はじめて測った質問");
    }

    @Test
    void noLinkWithoutAPublicUrl() {
        AuditDigest digest = new AuditDigest(44.6, null, null, List.of(), 0, 0);

        assertThat(NotificationService.htmlBody("案件", digest, null)).doesNotContain("解析結果を見る");
    }

    @Test
    void projectNameAndQuestionsAreEscaped() {
        AuditDigest digest = new AuditDigest(
                44.6, 38.9, 5.7, List.of(new VarianceLine("<b>質問</b>", 50.0, 40.0, 10.0)), 1, 0);

        String body = NotificationService.htmlBody("<script>", digest, null);

        assertThat(body).doesNotContain("<script>").doesNotContain("<b>質問</b>").contains("&lt;script&gt;");
    }

    @Test
    void resultUrlJoinsTheBaseAndTheJob() {
        UUID jobId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(NotificationService.resultUrl("https://app.example.com/", jobId)).isEqualTo(URL);
        assertThat(NotificationService.resultUrl(" https://app.example.com ", jobId)).isEqualTo(URL);
        assertThat(NotificationService.resultUrl("", jobId)).isNull();
        assertThat(NotificationService.resultUrl(null, jobId)).isNull();
    }
}
