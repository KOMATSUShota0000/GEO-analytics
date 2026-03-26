package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.entity.SgeResultEntity;
import com.geo.analytics.infrastructure.repository.SgeResultRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AsyncSgeMeasurementService {
    private final SgeMeasurementPort sgeMeasurementPort;
    private final SgeResultRepository sgeResultRepository;
    private final String serpApiKey;

    public AsyncSgeMeasurementService(
            SgeMeasurementPort sgeMeasurementPort,
            SgeResultRepository sgeResultRepository,
            @Value("${app.serpapi.api-key:}") String serpApiKey) {
        this.sgeMeasurementPort = sgeMeasurementPort;
        this.sgeResultRepository = sgeResultRepository;
        this.serpApiKey = serpApiKey;
    }

    @Async
    public void measureSgeForJob(JobEntity job, List<QueryEntity> queries) {
        UUID jobId = job.getId();
        int queryCount = queries == null ? 0 : queries.size();
        log.info("SGE measurement started jobId={} queryCount={}", jobId, queryCount);
        if (serpApiKey == null || serpApiKey.isBlank()) {
            log.warn("SGE measurement aborted jobId={} reason=app.serpapi.api-key is not configured", jobId);
            return;
        }
        if (queries == null || queries.isEmpty()) {
            return;
        }
        String brandName = job.getBrandName();
        for (int i = 0; i < queries.size(); i++) {
            if (i > 0) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.warn("SGE measurement interrupted during throttle jobId={}", jobId, interruptedException);
                    return;
                }
            }
            QueryEntity queryEntity = queries.get(i);
            String queryText = queryEntity.getQueryText();
            try {
                SgeMentionResult sgeMentionResult = sgeMeasurementPort.checkSgeMention(queryText, brandName);
                SgeResultEntity sgeResultEntity = new SgeResultEntity();
                sgeResultEntity.setJobId(jobId);
                sgeResultEntity.setWorkspaceId(job.getWorkspaceId());
                sgeResultEntity.setQueryId(queryEntity.getId());
                sgeResultEntity.setQuery(queryText);
                sgeResultEntity.setSgeRawResponse(sgeMentionResult.rawResponseJson());
                sgeResultEntity.setSgeMentioned(sgeMentionResult.mentioned());
                sgeResultRepository.save(sgeResultEntity);
            } catch (Exception exception) {
                log.error(
                    "SGE measurement failed for single query jobId={} queryId={} queryText={}",
                    jobId,
                    queryEntity.getId(),
                    queryText,
                    exception);
            }
        }
    }
}
