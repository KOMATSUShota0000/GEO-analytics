package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.JobPdfExportAuditEntity;
import com.geo.analytics.infrastructure.repository.JobPdfExportAuditRepository;
import com.geo.analytics.infrastructure.tenant.TenantContextHolder;
import com.geo.analytics.infrastructure.tenant.TenantIdentity;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PdfAuditService {

    private static final Logger log = LoggerFactory.getLogger(PdfAuditService.class);
    private static final int STACK_TRACE_LIMIT = 20_000;
    private static final String REPORT_KIND_JOB = "JOB_REPORT";

    private final JobPdfExportAuditRepository jobPdfExportAuditRepository;

    public PdfAuditService(JobPdfExportAuditRepository jobPdfExportAuditRepository) {
        this.jobPdfExportAuditRepository = jobPdfExportAuditRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulExport(UUID jobId, long pdfByteSize) {
        if (jobId == null) {
            return;
        }
        TenantIdentity identity = TenantContextHolder.requireContext();
        UUID tenantId = identity.tenantId();
        UUID organizationId = identity.organizationId();
        if (tenantId == null || organizationId == null) {
            log.warn("pdf_audit_skipped_missing_tenant jobId={}", jobId);
            return;
        }
        try {
            JobPdfExportAuditEntity entity = new JobPdfExportAuditEntity();
            entity.setId(UUID.randomUUID());
            entity.setTenantId(tenantId.toString());
            entity.setOrganizationId(organizationId);
            entity.setJobId(jobId);
            entity.setActorUserId(identity.userId());
            entity.setActorSessionId(null);
            entity.setPdfByteSize(Math.max(0L, pdfByteSize));
            entity.setReportKind(REPORT_KIND_JOB);
            OffsetDateTime now = OffsetDateTime.now();
            entity.setExportedAt(now);
            entity.setCreatedAt(now);
            jobPdfExportAuditRepository.save(entity);
        } catch (RuntimeException runtimeException) {
            log.error(
                    "pdf_audit_insert_failed jobId={} tenantId={} trace={}",
                    jobId,
                    tenantId,
                    truncateStackTrace(runtimeException));
        }
    }

    private static String truncateStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        String full = stringWriter.toString();
        if (full.length() <= STACK_TRACE_LIMIT) {
            return full;
        }
        return full.substring(0, STACK_TRACE_LIMIT);
    }
}
