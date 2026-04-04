package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.SgeMentionResult;
import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.entity.SgeResultEntity;
import com.geo.analytics.infrastructure.repository.SgeResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.geo.analytics.infrastructure.config.AppProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
public class AsyncSgeMeasurementService {
    private static final Logger log = LoggerFactory.getLogger(AsyncSgeMeasurementService.class);
    private final SgeMeasurementPort sgeMeasurementPort;
    private final SgeResultRepository sgeResultRepository;
    private final String serpApiKey;

    public AsyncSgeMeasurementService(
            SgeMeasurementPort sgeMeasurementPort,
            SgeResultRepository sgeResultRepository,
            AppProperties appProperties) {
        this.sgeMeasurementPort = sgeMeasurementPort;
        this.sgeResultRepository = sgeResultRepository;
        String key = appProperties.getSerpapi().getApiKey();
        this.serpApiKey = key != null ? key : "";
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
                if (sgeMentionResult == null) {
                    log.warn(
                        "SGE measurement skipped jobId={} queryId={} queryText={} reason=adapter returned null",
                        jobId,
                        queryEntity.getId(),
                        queryText);
                    continue;
                }
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
