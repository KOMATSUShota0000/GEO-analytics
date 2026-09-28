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

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final ProjectAuditNoticeReader projectAuditNoticeReader;
    private final JavaMailSender javaMailSender;
    private final String mailFrom;

    public NotificationService(
            ProjectAuditNoticeReader projectAuditNoticeReader,
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            AppProperties appProperties) {
        this.projectAuditNoticeReader = projectAuditNoticeReader;
        this.javaMailSender = javaMailSenderProvider.getIfAvailable();
        String from = appProperties.getNotifications().getMailFrom();
        this.mailFrom = from != null && !from.isBlank() ? from : "noreply@example.com";
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
        for (String recipient : notice.get().recipients()) {
            try {
                sendEmail(recipient, notice.get().projectName(), notice.get().digest());
            } catch (Exception exception) {
                log.error("email notify failed projectId={}", projectAuditCompletedEvent.projectId(), exception);
            }
        }
    }

    private void sendEmail(String to, String projectName, ProjectAuditNoticeReader.AuditDigest auditDigest) throws Exception {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
        helper.setFrom(Objects.requireNonNull(mailFrom));
        helper.setTo(to);
        helper.setSubject("[GEOアナリティクス] 監査完了 " + projectName);
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:system-ui,sans-serif'>");
        html.append("<h2>GEO監査が完了しました</h2>");
        html.append("<p><b>プロジェクト</b> ").append(HtmlUtils.htmlEscape(projectName)).append("</p>");
        html.append("<p><b>今回のSoM平均</b> ").append(auditDigest.currentAvg()).append("</p>");
        if (auditDigest.previousAvg() != null) {
            html.append("<p><b>前回監査のSoM平均</b> ").append(auditDigest.previousAvg()).append("</p>");
        }
        if (auditDigest.deltaAvg() != null) {
            html.append("<p><b>平均の増減</b> ").append(auditDigest.deltaAvg()).append("</p>");
        }
        html.append("<h3>変動TOP3</h3><ol>");
        for (ProjectAuditNoticeReader.VarianceLine line : auditDigest.top3()) {
            html.append("<li>")
                .append(HtmlUtils.htmlEscape(line.keyword()))
                .append(" — 今回 ")
                .append(line.currentSom() != null ? line.currentSom() : "—");
            if (line.previousSom() != null) {
                html.append(" / 前回 ").append(line.previousSom()).append(" / Δ").append(line.delta());
            }
            html.append("</li>");
        }
        html.append("</ol></body></html>");
        helper.setText(html.toString(), true);
        javaMailSender.send(mimeMessage);
    }
}
