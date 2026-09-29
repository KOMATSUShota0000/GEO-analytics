package com.geo.analytics.application.service;

import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final ProjectAuditNoticeReader projectAuditNoticeReader;
    private final JavaMailSender javaMailSender;
    private final String mailFrom;
    private final String publicBaseUrl;

    public NotificationService(
            ProjectAuditNoticeReader projectAuditNoticeReader,
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            AppProperties appProperties) {
        this.projectAuditNoticeReader = projectAuditNoticeReader;
        this.javaMailSender = javaMailSenderProvider.getIfAvailable();
        String from = appProperties.getNotifications().getMailFrom();
        this.mailFrom = from != null && !from.isBlank() ? from : "noreply@example.com";
        this.publicBaseUrl = appProperties.getPublicBaseUrl();
    }

    // Why: 宛先ごとに1通ずつ送る。1通に宛先を並べると受け取った人同士にアドレスが見え、
    //      1件のアドレスの不備でほかの宛先にも届かなくなるため（#174）。
    public void deliver(ProjectAuditCompletedEvent projectAuditCompletedEvent) {
        if (javaMailSender == null) {
            return;
        }
        Optional<ProjectAuditNoticeReader.AuditNotice> notice = TenantPlanScope.executeWithTenant(
            projectAuditCompletedEvent.workspaceId(),
            () -> projectAuditNoticeReader.read(projectAuditCompletedEvent.projectId(), projectAuditCompletedEvent.jobId()));
        if (notice.isEmpty()) {
            return;
        }
        String resultUrl = resultUrl(publicBaseUrl, projectAuditCompletedEvent.jobId());
        for (String recipient : notice.get().recipients()) {
            try {
                sendEmail(recipient, notice.get().projectName(), notice.get().digest(), resultUrl);
            } catch (Exception exception) {
                log.error("email notify failed projectId={}", projectAuditCompletedEvent.projectId(), exception);
            }
        }
    }

    private void sendEmail(String to, String projectName, ProjectAuditNoticeReader.AuditDigest auditDigest, String resultUrl)
            throws Exception {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
        helper.setFrom(Objects.requireNonNull(mailFrom));
        helper.setTo(to);
        helper.setSubject(subject(projectName));
        helper.setText(htmlBody(projectName, auditDigest, resultUrl), true);
        javaMailSender.send(mimeMessage);
    }

    static String subject(String projectName) {
        return "[GEOアナリティクス] " + projectName + " の解析が完了しました";
    }

    // Why: 本文は非エンジニアの担当者が読む。画面の呼び方（解析・SoMスコア）にそろえ、質問の行だけは
    //      オーナーの判断で「今回 / 前回 / Δ」の形を残す（#183）。
    static String htmlBody(String projectName, ProjectAuditNoticeReader.AuditDigest digest, String resultUrl) {
        String name = HtmlUtils.htmlEscape(projectName);
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:system-ui,sans-serif;line-height:1.7'>");
        html.append("<p>").append(name).append(" の解析が完了しました。</p>");
        html.append("<h3>■ AIの回答に取り上げられた割合（SoMスコア）の平均</h3>");
        html.append("<p>今回 ").append(digest.currentAvg());
        if (digest.previousAvg() != null && digest.deltaAvg() != null) {
            html.append("（前回 ").append(digest.previousAvg()).append(" から ").append(signed(digest.deltaAvg())).append("）");
        }
        html.append("</p>");
        html.append("<h3>■ 前回から大きく動いた質問</h3>");
        if (digest.previousAvg() == null) {
            html.append("<p>前回の解析がないため、比べられる質問はありません。</p>");
        } else {
            if (digest.top3().isEmpty()) {
                html.append("<p>前回と同じ質問がなかったため、比べられる質問はありません。</p>");
            } else {
                html.append("<ol>");
                for (ProjectAuditNoticeReader.VarianceLine line : digest.top3()) {
                    html.append("<li>")
                        .append(HtmlUtils.htmlEscape(line.keyword()))
                        .append(" — 今回 ").append(line.currentSom())
                        .append(" / 前回 ").append(line.previousSom())
                        .append(" / Δ").append(line.delta())
                        .append("</li>");
                }
                html.append("</ol>");
            }
            html.append("<p>※ 前回と同じ質問は").append(digest.comparedCount()).append("件でした。");
            if (digest.newQuestionCount() > 0) {
                html.append("今回はじめて測った質問が").append(digest.newQuestionCount()).append("件あります。");
            }
            html.append("</p>");
        }
        if (resultUrl != null) {
            String url = HtmlUtils.htmlEscape(resultUrl);
            html.append("<p>▶ <a href='").append(url).append("'>解析結果を見る</a><br>").append(url).append("</p>");
        }
        html.append("<p style='color:#64748b;font-size:12px'>このメールは、プロジェクト設定で登録された宛先にお送りしています。</p>");
        html.append("</body></html>");
        return html.toString();
    }

    static String resultUrl(String publicBaseUrl, UUID jobId) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank() || jobId == null) {
            return null;
        }
        String base = publicBaseUrl.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/job/" + jobId;
    }

    private static String signed(double value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }
}
