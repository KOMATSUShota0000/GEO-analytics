package com.geo.analytics.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.domain.event.ProjectAuditCompletedEvent;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final ProjectRepository projectRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final JavaMailSender javaMailSender;
    private final String mailFrom;
    private final RestClient restClient = RestClient.builder().build();

    public NotificationService(
            ProjectRepository projectRepository,
            AuditHistoryRepository auditHistoryRepository,
            JobRepository jobRepository,
            ObjectMapper objectMapper,
            ObjectProvider<JavaMailSender> javaMailSenderProvider,
            AppProperties appProperties) {
        this.projectRepository = projectRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.javaMailSender = javaMailSenderProvider.getIfAvailable();
        String from = appProperties.getNotifications().getMailFrom();
        this.mailFrom = from != null && !from.isBlank() ? from : "noreply@example.com";
    }

    public void deliver(ProjectAuditCompletedEvent projectAuditCompletedEvent) {
        UUID workspaceId = projectAuditCompletedEvent.workspaceId();
        TenantPlanScope.executeWithTenant(workspaceId, () -> {
            ProjectEntity projectEntity = projectRepository.findById(projectAuditCompletedEvent.projectId()).orElse(null);
            if (projectEntity == null) {
                return;
            }
            AuditDigest auditDigest = buildDigest(projectAuditCompletedEvent.jobId(), projectAuditCompletedEvent.projectId());
            String webhook = projectEntity.getSlackWebhookUrl();
            if (webhook != null && !webhook.isBlank()) {
                try {
                    postSlack(webhook, projectEntity.getName(), auditDigest);
                } catch (Exception exception) {
                    log.error("slack notify failed projectId={}", projectEntity.getId(), exception);
                }
            }
            String email = projectEntity.getNotificationEmail();
            if (email != null && !email.isBlank() && javaMailSender != null) {
                try {
                    sendEmail(email, projectEntity.getName(), auditDigest);
                } catch (Exception exception) {
                    log.error("email notify failed projectId={}", projectEntity.getId(), exception);
                }
            }
        });
    }

    private AuditDigest buildDigest(UUID jobId, UUID projectId) {
        List<AuditHistoryEntity> current = auditHistoryRepository.findByJobId(jobId);
        double currentAvg = current.stream()
            .map(AuditHistoryEntity::getSomScore)
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0d);
        Optional<JobEntity> prevJob = jobRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .filter(j -> JobStatus.COMPLETED.equals(j.getJobStatus()) && !j.getId().equals(jobId))
            .findFirst();
        Double previousAvg = null;
        Map<String, Double> prevByQuery = new HashMap<>();
        if (prevJob.isPresent()) {
            List<AuditHistoryEntity> prevRows = auditHistoryRepository.findByJobId(prevJob.get().getId());
            previousAvg = prevRows.stream()
                .map(AuditHistoryEntity::getSomScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d);
            for (AuditHistoryEntity row : prevRows) {
                prevByQuery.put(row.getQuery(), row.getSomScore());
            }
        }
        Double deltaAvg = previousAvg == null ? null : round1(currentAvg - previousAvg);
        List<VarianceLine> variances = new ArrayList<>();
        for (AuditHistoryEntity row : current) {
            Double p = prevByQuery.get(row.getQuery());
            Double curSom = row.getSomScore();
            double d = (p == null || curSom == null) ? 0d : curSom - p;
            variances.add(new VarianceLine(row.getQuery(), curSom, p, d));
        }
        variances.sort(Comparator.comparing((VarianceLine v) -> Math.abs(v.delta())).reversed());
        List<VarianceLine> top3 = variances.stream().limit(3).toList();
        return new AuditDigest(round1(currentAvg), previousAvg == null ? null : round1(previousAvg), deltaAvg, top3);
    }

    private static Double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private void postSlack(String webhookUrl, String projectName, AuditDigest auditDigest) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode blocks = root.putArray("blocks");
        ObjectNode header = blocks.addObject();
        header.put("type", "header");
        header.putObject("text").put("type", "plain_text").put("text", "GEO監査が完了しました").put("emoji", true);
        ObjectNode sec1 = blocks.addObject();
        sec1.put("type", "section");
        StringBuilder md = new StringBuilder();
        md.append("*プロジェクト:* ").append(projectName).append("\n");
        md.append("*今回のSoM平均:* ").append(auditDigest.currentAvg());
        if (auditDigest.previousAvg() != null) {
            md.append("\n*前回監査のSoM平均:* ").append(auditDigest.previousAvg());
        }
        if (auditDigest.deltaAvg() != null) {
            md.append("\n*平均の増減:* ").append(auditDigest.deltaAvg() >= 0 ? "+" : "").append(auditDigest.deltaAvg());
        } else {
            md.append("\n*比較:* 前回監査データなし（初回または比較対象なし）");
        }
        sec1.putObject("text").put("type", "mrkdwn").put("text", md.toString());
        ObjectNode sec2 = blocks.addObject();
        sec2.put("type", "section");
        StringBuilder top = new StringBuilder();
        top.append("*変動TOP3（キーワード別SoM差分）*\n");
        int i = 1;
        for (VarianceLine line : auditDigest.top3()) {
            top.append(i++).append(". `")
                .append(escapeSlack(line.keyword()))
                .append("` 今回 ")
                .append(line.currentSom() != null ? String.valueOf(round1(line.currentSom())) : "—");
            if (line.previousSom() != null) {
                top.append(" → 前回 ").append(round1(line.previousSom())).append(" （Δ").append(round1(line.delta())).append("）");
            } else {
                top.append(" （前回なし）");
            }
            top.append("\n");
        }
        if (auditDigest.top3().isEmpty()) {
            top.append("データなし");
        }
        sec2.putObject("text").put("type", "mrkdwn").put("text", top.toString());
        String json = objectMapper.writeValueAsString(root);
        restClient.post()
            .uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json)
            .retrieve()
            .toBodilessEntity();
    }

    private static String escapeSlack(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void sendEmail(String to, String projectName, AuditDigest auditDigest) throws Exception {
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
        for (VarianceLine line : auditDigest.top3()) {
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

    private record AuditDigest(double currentAvg, Double previousAvg, Double deltaAvg, List<VarianceLine> top3) {
    }

    private record VarianceLine(String keyword, Double currentSom, Double previousSom, double delta) {
    }
}
