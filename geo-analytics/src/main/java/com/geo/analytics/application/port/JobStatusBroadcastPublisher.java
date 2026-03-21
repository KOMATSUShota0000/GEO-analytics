package com.geo.analytics.application.port;

import com.geo.analytics.domain.entity.JobEntity;

public interface JobStatusBroadcastPublisher {
    void publish(JobEntity jobEntity);
}
