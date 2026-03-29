package com.geo.analytics.infrastructure.messaging;

import com.geo.analytics.application.dto.JobStompStatusPayload;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.application.service.JobPersistenceService;
import com.geo.analytics.application.service.StrategyInsightService;
import com.geo.analytics.domain.entity.JobEntity;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobStatusStompPublisher implements JobStatusBroadcastPublisher {
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final JobPersistenceService jobPersistenceService;
    private final StrategyInsightService strategyInsightService;

    public JobStatusStompPublisher(
            SimpMessagingTemplate simpMessagingTemplate,
            @Lazy JobPersistenceService jobPersistenceService,
            StrategyInsightService strategyInsightService) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.jobPersistenceService = jobPersistenceService;
        this.strategyInsightService = strategyInsightService;
    }

    @Override
    public void publish(JobEntity jobEntity) {
        if (jobEntity == null || jobEntity.getId() == null) {
            return;
        }
        var rollup = strategyInsightService.rollupJob(jobPersistenceService.findResultsByJobId(jobEntity.getId()));
        JobStompStatusPayload payload = JobStompStatusPayload.from(jobEntity, rollup);
        simpMessagingTemplate.convertAndSend("/topic/jobs/" + jobEntity.getId(), payload);
    }
}
