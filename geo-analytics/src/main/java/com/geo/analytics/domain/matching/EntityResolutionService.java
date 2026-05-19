package com.geo.analytics.domain.matching;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import java.lang.ScopedValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

@Service("scopedMatchingEntityResolutionService")
public final class EntityResolutionService {

    public static final ScopedValue<EntityResolutionScope> RESOLUTION_SCOPE = ScopedValue.newInstance();
    public static final int DEFAULT_IMMEDIATE_THRESHOLD = 32;

    private static final Comparator<ScoredRow> RANK_ORDER = Comparator
            .comparingDouble(ScoredRow::fullScore).reversed()
            .thenComparingInt(r -> r.spec().candidateSurface().length())
            .thenComparing(r -> r.spec().candidateSurface(), CharSequence::compare);

    private final NormalizationLayer normalizationLayer;
    private final ExecutorService virtualYieldExecutor;
    private final Semaphore deepAnalysisParallelism;
    private final int immediateThreshold;

    public EntityResolutionService(NormalizationLayer normalizationLayer) {
        this.normalizationLayer = Objects.requireNonNull(normalizationLayer);
        this.virtualYieldExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.deepAnalysisParallelism =
                new Semaphore(Math.max(1, Runtime.getRuntime().availableProcessors()));
        this.immediateThreshold = Math.max(1, DEFAULT_IMMEDIATE_THRESHOLD);
    }

    public static <T, X extends Throwable> T callInScope(EntityResolutionScope scope, ScopedValue.CallableOp<T, X> action) throws X {
        return ScopedValue.where(RESOLUTION_SCOPE, Objects.requireNonNull(scope)).call(action);
    }

    public static void runInScope(EntityResolutionScope scope, Runnable action) {
        ScopedValue.where(RESOLUTION_SCOPE, Objects.requireNonNull(scope)).run(action);
    }

    public List<EntityResolutionRankedRow> resolveWhenScoped(List<EntityMatchSpec> specs) {
        RESOLUTION_SCOPE.get();
        return resolveAllBound(specs);
    }

    public List<EntityResolutionRankedRow> resolveAllInScope(EntityResolutionScope scope, List<EntityMatchSpec> specs) throws Exception {
        return callInScope(scope, () -> resolveAllBound(specs));
    }

    private List<EntityResolutionRankedRow> resolveAllBound(List<EntityMatchSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }
        List<ScoredRow> rows;
        if (specs.size() <= immediateThreshold) {
            rows = resolveImmediateScored(specs);
        } else {
            rows = resolveDeepScored(specs);
        }
        return applyRanking(rows);
    }

    private List<ScoredRow> resolveImmediateScored(List<EntityMatchSpec> specs) {
        EntityResolutionScope ctx = RESOLUTION_SCOPE.get();
        CompletableFuture.runAsync(() -> LockSupport.parkNanos(1_000_000L), virtualYieldExecutor).join();
        int n = specs.size();
        ScoredRow[] buf = new ScoredRow[n];
        // 小規模データ（immediateThreshold=32件以下）では逐次が速く、共有 ForkJoinPool(commonPool) も汚さない
        for (int i = 0; i < n; i++) {
            buf[i] = this.buildScoredRow(specs.get(i), ctx);
        }
        return List.copyOf(Arrays.asList(buf));
    }

    private List<ScoredRow> resolveDeepScored(List<EntityMatchSpec> specs) {
        try (StructuredTaskScope<ScoredRow, Stream<Subtask<ScoredRow>>> scope =
                StructuredTaskScope.open(StructuredTaskScope.Joiner.<ScoredRow>allSuccessfulOrThrow())) {
            for (EntityMatchSpec spec : specs) {
                scope.fork(() -> {
                    deepAnalysisParallelism.acquireUninterruptibly();
                    try {
                        return EntityResolutionService.this.buildScoredRow(spec, RESOLUTION_SCOPE.get());
                    } finally {
                        deepAnalysisParallelism.release();
                    }
                });
            }
            try (Stream<Subtask<ScoredRow>> joined = scope.join()) {
                return joined.map(Subtask::get).toList();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (StructuredTaskScope.FailedException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause != null ? cause : exception);
        }
    }

    private ScoredRow buildScoredRow(EntityMatchSpec spec, EntityResolutionScope ctx) {
        Objects.requireNonNull(ctx);
        if (ctx.index().lookupRow(spec.bigramKey()) < 0) {
            double z = 0.0;
            return new ScoredRow(spec, z, RobustAuditMathUtil.finalizeScore(z));
        }
        double full = HybridEntityResolutionEngine.fullScoreWithNormalization(
                normalizationLayer,
                spec.querySurface(),
                spec.candidateSurface(),
                ctx.smoothingN(),
                ctx.smoothingC(),
                ctx.priorMu());
        return new ScoredRow(spec, full, RobustAuditMathUtil.finalizeScore(full));
    }

    private static List<EntityResolutionRankedRow> applyRanking(List<ScoredRow> rows) {
        ArrayList<ScoredRow> sorted = new ArrayList<>(rows);
        sorted.sort(RANK_ORDER);
        ArrayList<EntityResolutionRankedRow> out = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            ScoredRow r = sorted.get(i);
            out.add(new EntityResolutionRankedRow(i + 1, r.finalizedScore(), r.spec()));
        }
        return List.copyOf(out);
    }

    @PreDestroy
    public void shutdownVirtualYield() {
        virtualYieldExecutor.close();
    }

    public record EntityResolutionRankedRow(int rank, double finalizedScore, EntityMatchSpec spec) {
    }

    private record ScoredRow(EntityMatchSpec spec, double fullScore, double finalizedScore) {
    }

    public record EntityResolutionScope(
            String tenantId,
            long auditContext,
            BigramInvertedIndex index,
            double smoothingN,
            double smoothingC,
            double priorMu) {
    }

    public record EntityMatchSpec(String bigramKey, CharSequence querySurface, CharSequence candidateSurface) {
    }
}
