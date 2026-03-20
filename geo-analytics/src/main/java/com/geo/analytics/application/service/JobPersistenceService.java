package com.geo.analytics.application.service;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.entity.ResultEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.repository.JobRepository;
import com.geo.analytics.infrastructure.repository.QueryRepository;
import com.geo.analytics.infrastructure.repository.ResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JobPersistenceService {
    private final JobRepository jobRepository;
    private final QueryRepository queryRepository;
    private final ResultRepository resultRepository;

    public JobPersistenceService(
            JobRepository jobRepository,
            QueryRepository queryRepository,
            ResultRepository resultRepository) {
        this.jobRepository = jobRepository;
        this.queryRepository = queryRepository;
        this.resultRepository = resultRepository;
    }

    public JobEntity findJobById(UUID jobId) {
        return jobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
    }

    public Optional<JobEntity> findJobByIdOptional(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    public List<JobEntity> findJobsByStatus(JobStatus jobStatus) {
        return jobRepository.findByJobStatus(jobStatus);
    }

    public List<QueryEntity> findUnprocessedQueriesByJobId(UUID jobId) {
        return queryRepository.findByJobIdAndProcessedFalse(jobId);
    }

    public List<QueryEntity> findQueriesByJobId(UUID jobId) {
        return queryRepository.findByJobId(jobId);
    }

    public Optional<QueryEntity> findQueryById(UUID queryId) {
        return queryRepository.findById(queryId);
    }

    public List<ResultEntity> findResultsByJobId(UUID jobId) {
        return resultRepository.findByJobId(jobId);
    }

    @Transactional
    public JobEntity createJob(String brandName) {
        JobEntity jobEntity = new JobEntity();
        jobEntity.setBrandName(brandName);
        return jobRepository.save(jobEntity);
    }

    @Transactional
    public void registerQueriesAndTransitionToFileUploaded(UUID jobId, List<String> queryTexts) {
        JobEntity jobEntity = jobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
        if (jobEntity.getJobStatus() != JobStatus.CREATED) {
            throw new IllegalStateException(
                "Queries can only be added to a CREATED job. Current status: " + jobEntity.getJobStatus());
        }
        queryTexts.forEach(queryText -> {
            QueryEntity queryEntity = new QueryEntity();
            queryEntity.setJobId(jobId);
            queryEntity.setQueryText(queryText);
            queryRepository.save(queryEntity);
        });
        jobEntity.setJobStatus(JobStatus.FILE_UPLOADED);
        jobRepository.save(jobEntity);
    }

    @Transactional
    public void updateJobStatus(UUID jobId, JobStatus newJobStatus, String errorMessage) {
        JobEntity jobEntity = jobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
        jobEntity.setJobStatus(newJobStatus);
        jobEntity.setErrorMessage(errorMessage);
        jobRepository.save(jobEntity);
    }

    @Transactional
    public void updateJobStatusToRunningWithGeminiJobName(UUID jobId, String geminiJobName) {
        JobEntity jobEntity = jobRepository.findById(jobId)
            .orElseThrow(() -> new EntityNotFoundException("Job not found: " + jobId));
        jobEntity.setJobStatus(JobStatus.RUNNING);
        jobEntity.setGeminiJobName(geminiJobName);
        jobRepository.save(jobEntity);
    }

    @Transactional
    public void saveResultAndMarkQueryAsProcessed(ResultEntity resultEntity, UUID queryId) {
        resultRepository.save(resultEntity);
        queryRepository.findById(queryId).ifPresent(queryEntity -> {
            queryEntity.setProcessed(true);
            queryRepository.save(queryEntity);
        });
    }

    @Transactional
    public void saveAllResultsAndMarkQueriesAsProcessed(
            List<ResultEntity> resultEntities, List<UUID> processedQueryIds) {
        resultRepository.saveAll(resultEntities);
        List<QueryEntity> processedQueryEntities = queryRepository.findAllById(processedQueryIds);
        processedQueryEntities.forEach(queryEntity -> queryEntity.setProcessed(true));
        queryRepository.saveAll(processedQueryEntities);
    }
}
