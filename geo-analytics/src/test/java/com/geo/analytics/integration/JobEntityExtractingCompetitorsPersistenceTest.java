package com.geo.analytics.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.geo.analytics.GeoAnalyticsApplication;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.api.SerpApiAdapter;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        classes = GeoAnalyticsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class JobEntityExtractingCompetitorsPersistenceTest extends PostgresSuperuserTestBase {

    @MockitoBean
    private SerpApiAdapter serpApiAdapter;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void extractingCompetitorsStatus_roundTripsThroughJpa() {
        JobEntity jobEntity = new JobEntity();
        jobEntity.setWorkspaceId(DefaultTenantIds.WORKSPACE_ID);
        jobEntity.setBrandName("persist-extracting-competitors");
        jobEntity.setTargetUrl("https://example.test/persist-extracting-competitors");
        jobEntity.setJobStatus(JobStatus.EXTRACTING_COMPETITORS);
        UUID id = jobRepository.saveAndFlush(jobEntity).getId();
        entityManager.clear();
        JobEntity loaded = jobRepository.findById(id).orElseThrow();
        assertThat(loaded.getJobStatus()).isEqualTo(JobStatus.EXTRACTING_COMPETITORS);
    }
}
