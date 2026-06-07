package com.geo.analytics.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.geo.analytics.application.port.SgeMeasurementPort;
import com.geo.analytics.domain.entity.JobEntity;
import com.geo.analytics.domain.entity.QueryEntity;
import com.geo.analytics.domain.enums.JobStatus;
import com.geo.analytics.infrastructure.config.AppProperties;
import com.geo.analytics.infrastructure.ratelimit.SerpApiGlobalRequestGate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncSgeMeasurementServiceDegradeTest {

    private static final UUID JOB_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID WORKSPACE_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1");

    private AppProperties propsWithKey(String key) {
        AppProperties.Serpapi serp = new AppProperties.Serpapi();
        serp.setApiKey(key);
        AppProperties props = new AppProperties();
        props.setSerpapi(serp);
        return props;
    }

    private QueryEntity query(String text) {
        QueryEntity q = new QueryEntity();
        q.setId(UUID.randomUUID());
        q.setQueryText(text);
        return q;
    }

    private JobEntity job() {
        JobEntity j = new JobEntity();
        j.setId(JOB_ID);
        j.setWorkspaceId(WORKSPACE_ID);
        j.setBrandName("AcmeBrand");
        return j;
    }

    @Test
    void blankSerpApiKey_writesEmptyPlaceholderForEachQuery_andDoesNotFailJob() {
        SgeMeasurementPort port = mock(SgeMeasurementPort.class);
        BatchPersistenceService persistence = mock(BatchPersistenceService.class);
        PlanBasedQuotaManager quota = mock(PlanBasedQuotaManager.class);
        SerpApiGlobalRequestGate gate = mock(SerpApiGlobalRequestGate.class);

        AsyncSgeMeasurementService svc = new AsyncSgeMeasurementService(
                port, persistence, quota, gate, propsWithKey(""));

        List<QueryEntity> queries = List.of(query("q1"), query("q2"), query("q3"));
        svc.measureSgeForJob(job(), queries, 0);

        verify(persistence, times(3)).insertSgeResult(
                eq(WORKSPACE_ID), eq(JOB_ID), any(UUID.class),
                anyString(), eq("{}"), eq(false), eq(0));
        verify(persistence, never()).updateJobStatus(eq(JOB_ID), eq(JobStatus.FAILED), anyString());
        verifyNoInteractions(port, gate);
    }

    @Test
    void blankSerpApiKey_emptyQueryList_writesNothing_andDoesNotFailJob() {
        SgeMeasurementPort port = mock(SgeMeasurementPort.class);
        BatchPersistenceService persistence = mock(BatchPersistenceService.class);
        PlanBasedQuotaManager quota = mock(PlanBasedQuotaManager.class);
        SerpApiGlobalRequestGate gate = mock(SerpApiGlobalRequestGate.class);

        AsyncSgeMeasurementService svc = new AsyncSgeMeasurementService(
                port, persistence, quota, gate, propsWithKey(""));

        svc.measureSgeForJob(job(), List.of(), 0);

        verifyNoInteractions(port, gate);
        verify(persistence, never()).insertSgeResult(
                any(UUID.class), any(UUID.class), any(UUID.class),
                anyString(), anyString(), anyBoolean(), anyInt());
        verify(persistence, never()).updateJobStatus(eq(JOB_ID), eq(JobStatus.FAILED), anyString());
    }

    @Test
    void serpApiCallFails_degradesToEmptyPlaceholder_andDoesNotFailJob() throws Exception {
        SgeMeasurementPort port = mock(SgeMeasurementPort.class);
        BatchPersistenceService persistence = mock(BatchPersistenceService.class);
        PlanBasedQuotaManager quota = mock(PlanBasedQuotaManager.class);
        SerpApiGlobalRequestGate gate = mock(SerpApiGlobalRequestGate.class);
        // SerpAPI 呼び出しが接続タイムアウト等で失敗するケースを模す（キーは設定済み）。
        when(gate.execute(any())).thenThrow(new IllegalStateException("connect timed out"));

        AsyncSgeMeasurementService svc = new AsyncSgeMeasurementService(
                port, persistence, quota, gate, propsWithKey("dummy-key"));

        List<QueryEntity> queries = List.of(query("q1"), query("q2"));
        svc.measureSgeForJob(job(), queries, 0);

        // 各クエリは空プレースホルダ（"{}" / false / 0）へ降格して保存され、ジョブは FAILED にならない。
        verify(persistence, times(2)).insertSgeResult(
                eq(WORKSPACE_ID), eq(JOB_ID), any(UUID.class),
                anyString(), eq("{}"), eq(false), eq(0));
        verify(persistence, never()).updateJobStatus(eq(JOB_ID), eq(JobStatus.FAILED), anyString());
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
