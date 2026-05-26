package com.geo.analytics.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class JobKnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(JobKnowledgeIngestionService.class);

    private final KnowledgeFileStorageService knowledgeFileStorageService;
    private final KnowledgeFileParsingService knowledgeFileParsingService;
    private final JobPersistenceService jobPersistenceService;

    public JobKnowledgeIngestionService(
            KnowledgeFileStorageService knowledgeFileStorageService,
            KnowledgeFileParsingService knowledgeFileParsingService,
            JobPersistenceService jobPersistenceService) {
        this.knowledgeFileStorageService = knowledgeFileStorageService;
        this.knowledgeFileParsingService = knowledgeFileParsingService;
        this.jobPersistenceService = jobPersistenceService;
    }

    public void ingest(UUID jobId, MultipartFile[] files) {
        try {
            List<Path> paths = knowledgeFileStorageService.storeUploadedFiles(jobId, files);
            String combined = knowledgeFileParsingService.parseAndConcatenate(paths);
            if (combined != null && !combined.isBlank()) {
                jobPersistenceService.updateExtractedKnowledge(jobId, combined);
            }
        } catch (Exception e) {
            log.warn("Knowledge ingestion failed jobId={}", jobId, e);
        } finally {
            knowledgeFileStorageService.deleteStagingDirectory(jobId);
        }
    }
}
