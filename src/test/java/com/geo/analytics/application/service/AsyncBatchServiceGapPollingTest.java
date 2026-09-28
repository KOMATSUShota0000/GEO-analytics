package com.geo.analytics.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.infrastructure.ai.GeminiBatchClient;
import com.geo.analytics.infrastructure.ai.dto.GeminiBatchJob;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ギャップ分析（Pro 以上）のバッチが終わっても「解析完了」の合図を出し直さない（#176）。
 * 出し直すと、お知らせが2通届き、スナップショットと最終監査日時も2回動いていた。
 */
class AsyncBatchServiceGapPollingTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final String GAP_BATCH = "batches/gap-176";
    private static final String SUCCEEDED = "JOB_STATE_SUCCEEDED";

    @Test
    void gapAnalysisCompletionDoesNotAnnounceTheAuditAgain() {
        BatchPersistenceService batchPersistence = mock(BatchPersistenceService.class);
        GeminiBatchClient geminiBatchClient = mock(GeminiBatchClient.class);
        GapAnalysisBatchProcessor gapAnalysisBatchProcessor = mock(GapAnalysisBatchProcessor.class);
        ProjectAuditLifecyclePublisher publisher = mock(ProjectAuditLifecyclePublisher.class);
        AsyncBatchService service = new AsyncBatchService(
                batchPersistence,
                mock(GeminiBatchExecutorService.class),
                geminiBatchClient,
                mock(GeminiResultProcessor.class),
                publisher,
                gapAnalysisBatchProcessor,
                mock(GapAnalysisService.class),
                mock(PlanBasedQuotaManager.class),
                mock(JobBenchmarkCaptureService.class),
                mock(AiRubricAuditService.class));
        JobEntity job = new JobEntity();
        job.setId(JOB_ID);
        job.setGapAnalysisGeminiJobName(GAP_BATCH);
        when(batchPersistence.findJobsPendingGapAnalysisOutput()).thenReturn(List.of(job));
        GeminiBatchJob batch = mock(GeminiBatchJob.class);
        when(batch.state()).thenReturn(SUCCEEDED);
        when(geminiBatchClient.getBatchJobStatus(GAP_BATCH)).thenReturn(batch);
        when(geminiBatchClient.shouldAwaitNextPoll(SUCCEEDED)).thenReturn(false);
        when(geminiBatchClient.isTerminalState(SUCCEEDED)).thenReturn(true);
        when(geminiBatchClient.isSucceededState(SUCCEEDED)).thenReturn(true);
        when(geminiBatchClient.resolveBatchOutputFileName(batch)).thenReturn("files/gap-output");
        when(geminiBatchClient.downloadOutputFileContent("files/gap-output")).thenReturn("{}");

        service.pollGapAnalysisBatches();

        // Why: 巡回は CompletableFuture で別スレッドに流れるため、結果の保存を待ってから合図が出ないことを確かめる
        verify(gapAnalysisBatchProcessor, timeout(5000)).processOutputJsonl(JOB_ID, "{}");
        verify(publisher, after(500).never()).publishAuditCompleted(any());
    }
}
