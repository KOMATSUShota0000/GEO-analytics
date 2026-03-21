package com.geo.analytics.infrastructure.messaging;

import com.geo.analytics.application.dto.JobStompStatusPayload;
import com.geo.analytics.application.port.JobStatusBroadcastPublisher;
import com.geo.analytics.domain.entity.JobEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobStatusStompPublisher implements JobStatusBroadcastPublisher {
    private final SimpMessagingTemplate simpMessagingTemplate;

    public JobStatusStompPublisher(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Override
    public void publish(JobEntity jobEntity) {
        if (jobEntity == null || jobEntity.getId() == null) {
            return;
        }
        JobStompStatusPayload payload = JobStompStatusPayload.from(jobEntity);
        simpMessagingTemplate.convertAndSend("/topic/jobs/" + jobEntity.getId(), payload);
    }
}
