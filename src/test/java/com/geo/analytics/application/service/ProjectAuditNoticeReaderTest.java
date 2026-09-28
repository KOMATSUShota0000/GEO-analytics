package com.geo.analytics.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.entity.AuditHistoryEntity;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.ProjectEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.repository.AuditHistoryRepository;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** お知らせに載せる内容（#174）。RLS を通した読み出しは ProjectNotificationIntegrationTest で確かめる。 */
class ProjectAuditNoticeReaderTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID JOB_ID = UUID.randomUUID();
    private static final UUID PREVIOUS_JOB_ID = UUID.randomUUID();

    private ProjectRepository projectRepository;
    private AuditHistoryRepository auditHistoryRepository;
    private JobRepository jobRepository;
    private ProjectAuditNoticeReader reader;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        auditHistoryRepository = mock(AuditHistoryRepository.class);
        jobRepository = mock(JobRepository.class);
        reader = new ProjectAuditNoticeReader(projectRepository, auditHistoryRepository, jobRepository);
    }

    @Test
    void valuesInTheMailAreRoundedToOneDecimal_butOrderedByTheUnroundedChange() {
        givenProject(List.of("a@example.com"));
        when(auditHistoryRepository.findByJobId(JOB_ID)).thenReturn(List.of(
                row("質問A", 12.3456),
                row("質問B", 50.04)));
        when(jobRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID)).thenReturn(List.of(job(JOB_ID), job(PREVIOUS_JOB_ID)));
        when(auditHistoryRepository.findByJobId(PREVIOUS_JOB_ID)).thenReturn(List.of(
                row("質問A", 10.0),
                row("質問B", 50.0)));

        ProjectAuditNoticeReader.AuditDigest digest = reader.read(PROJECT_ID, JOB_ID).orElseThrow().digest();

        assertThat(digest.top3()).extracting(ProjectAuditNoticeReader.VarianceLine::keyword).containsExactly("質問A", "質問B");
        ProjectAuditNoticeReader.VarianceLine top = digest.top3().get(0);
        assertThat(top.currentSom()).isEqualTo(12.3);
        assertThat(top.previousSom()).isEqualTo(10.0);
        assertThat(top.delta()).isEqualTo(2.3);
        assertThat(digest.currentAvg()).isEqualTo(31.2);
        assertThat(digest.previousAvg()).isEqualTo(30.0);
        assertThat(digest.deltaAvg()).isEqualTo(1.2);
    }

    @Test
    void queryWithoutPreviousValueKeepsNull() {
        givenProject(List.of("a@example.com"));
        when(auditHistoryRepository.findByJobId(JOB_ID)).thenReturn(List.of(row("新しい質問", 7.77)));
        when(jobRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID)).thenReturn(List.of(job(JOB_ID)));

        ProjectAuditNoticeReader.VarianceLine line = reader.read(PROJECT_ID, JOB_ID).orElseThrow().digest().top3().get(0);

        assertThat(line.currentSom()).isEqualTo(7.8);
        assertThat(line.previousSom()).isNull();
    }

    @Test
    void noNoticeWithoutRecipients() {
        givenProject(List.of());

        assertThat(reader.read(PROJECT_ID, JOB_ID)).isEmpty();
    }

    @Test
    void noNoticeWhenProjectIsNotVisible() {
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(reader.read(PROJECT_ID, JOB_ID)).isEmpty();
    }

    private void givenProject(List<String> recipients) {
        ProjectEntity project = new ProjectEntity();
        project.setId(PROJECT_ID);
        project.setName("テスト案件");
        project.setNotificationEmails(recipients);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private static AuditHistoryEntity row(String query, double som) {
        AuditHistoryEntity row = new AuditHistoryEntity();
        row.setQuery(query);
        row.setSomScore(som);
        return row;
    }

    private static JobEntity job(UUID id) {
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setJobStatus(JobStatus.COMPLETED);
        return job;
    }
}
