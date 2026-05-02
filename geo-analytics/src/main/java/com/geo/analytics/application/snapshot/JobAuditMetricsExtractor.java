package com.geo.analytics.application.snapshot;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JobAuditMetricsExtractor {

    public record SnapshotMetricInputs(
            double aiAuditPercent, double meoTrustPercent, double machineReadabilityPercent, long localTrustCount) {}

    private final AuditHistoryRepository auditHistoryRepository;

    public JobAuditMetricsExtractor(AuditHistoryRepository auditHistoryRepository) {
        this.auditHistoryRepository = auditHistoryRepository;
    }

    public SnapshotMetricInputs extract(UUID jobId) {
        List<AuditHistoryEntity> rows = auditHistoryRepository.findByJobId(jobId);
        double sum = 0.0;
        int n = 0;
        for (AuditHistoryEntity row : rows) {
            Double s = row.getSomScore();
            if (s != null && !Double.isNaN(s) && !Double.isInfinite(s)) {
                sum += s;
                n++;
            }
        }
        double ai = n > 0 ? Math.clamp(sum / (double) n, 0.0, 100.0) : 0.0;
        return new SnapshotMetricInputs(ai, 0.0, 0.0, 0L);
    }
}
