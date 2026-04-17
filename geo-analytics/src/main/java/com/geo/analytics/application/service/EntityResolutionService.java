package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ResolutionResult;
import com.geo.analytics.domain.entity.UnresolvedEntityQueueEntity;
import com.geo.analytics.domain.matching.ZeroAllocationTokenizer;
import com.geo.analytics.domain.model.ResolvableEntity;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.EntityResolutionBlockingService;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.infrastructure.ai.DeepSeekAdapter;
import com.geo.analytics.infrastructure.repository.UnresolvedEntityQueueRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantPlanScope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public final class EntityResolutionService {

    public static final String CALCULATION_VERSION = "ER_PIPELINE_V1";
    private static final double LEXICAL_DICE_HIGH = 0.85;
    private static final double LEXICAL_DICE_LOW = 0.70;
    private static final double LLM_CONFIDENCE_MIN = 0.90;

    private static final ConcurrentLinkedQueue<int[]> BIGRAM_BUFFER_POOL = new ConcurrentLinkedQueue<>();

    private final JapaneseNlpService japaneseNlpService;
    private final EntityResolutionBlockingService entityResolutionBlockingService;
    private final DeepSeekAdapter deepSeekAdapter;
    private final UnresolvedEntityQueueRepository unresolvedEntityQueueRepository;

    public EntityResolutionService(
            JapaneseNlpService japaneseNlpService,
            EntityResolutionBlockingService entityResolutionBlockingService,
            DeepSeekAdapter deepSeekAdapter,
            UnresolvedEntityQueueRepository unresolvedEntityQueueRepository) {
        this.japaneseNlpService = japaneseNlpService;
        this.entityResolutionBlockingService = entityResolutionBlockingService;
        this.deepSeekAdapter = deepSeekAdapter;
        this.unresolvedEntityQueueRepository = unresolvedEntityQueueRepository;
    }

    public Map<String, List<ResolvableEntity>> bucketEntities(List<ResolvableEntity> entities) {
        return entityResolutionBlockingService.bucketByBlockingHash(entities);
    }

    public ResolutionResult resolvePair(String labelA, String labelB) {
        String rawA = labelA != null ? labelA : "";
        String rawB = labelB != null ? labelB : "";
        String prepA = EntityNormalizer.prepareForSudachi(rawA);
        String prepB = EntityNormalizer.prepareForSudachi(rawB);
        String nfa = japaneseNlpService.normalizedForm(prepA);
        String nfb = japaneseNlpService.normalizedForm(prepB);
        if (!nfa.isBlank() && nfa.equals(nfb)) {
            return new ResolutionResult.Merged(
                    pickCanonical(rawA, rawB, prepA, prepB),
                    1,
                    1.0,
                    CALCULATION_VERSION);
        }
        String ha = entityResolutionBlockingService.blockingHashSha256(rawA);
        String hb = entityResolutionBlockingService.blockingHashSha256(rawB);
        if (!ha.equals(hb)) {
            return persistPending(rawA, rawB, ha, hb);
        }
        double dice = lexicalDiceOnNormalized(nfa, nfb);
        if (dice >= LEXICAL_DICE_HIGH) {
            return new ResolutionResult.Merged(
                    pickCanonical(rawA, rawB, prepA, prepB),
                    2,
                    dice,
                    CALCULATION_VERSION);
        }
        if (dice >= LEXICAL_DICE_LOW && dice < LEXICAL_DICE_HIGH) {
            var judgment = deepSeekAdapter.judgeEntityIdentityBlocking(prepA, prepB);
            if (judgment.sameEntity() && judgment.confidence() >= LLM_CONFIDENCE_MIN) {
                return new ResolutionResult.Merged(
                        pickCanonical(rawA, rawB, prepA, prepB),
                        3,
                        dice,
                        CALCULATION_VERSION);
            }
        }
        return persistPending(rawA, rawB, ha, hb);
    }

    private ResolutionResult persistPending(String left, String right, String ha, String hb) {
        try {
            var row = new UnresolvedEntityQueueEntity();
            var tid = TenantPlanScope.getTenantId();
            row.setWorkspaceId(
                    tid != null && !tid.isBlank() ? UUID.fromString(tid) : DefaultTenantIds.WORKSPACE_ID);
            row.setLeftLabel(left);
            row.setRightLabel(right);
            row.setLeftBlockingHash(ha);
            row.setRightBlockingHash(hb);
            row.setManualReviewRequired(true);
            row.setCalculationVersion(CALCULATION_VERSION);
            unresolvedEntityQueueRepository.save(row);
        } catch (Exception exception) {
        }
        return new ResolutionResult.PendingManualReview(
                left,
                right,
                ha,
                hb,
                true,
                CALCULATION_VERSION);
    }

    private static double lexicalDiceOnNormalized(String nfa, String nfb) {
        if (nfa.isBlank() && nfb.isBlank()) {
            return 1.0;
        }
        int[] wA = borrowBigramBuffer();
        int[] wB = borrowBigramBuffer();
        try {
            return ZeroAllocationTokenizer.diceCoefficient(nfa, nfb, wA, wB);
        } finally {
            releaseBigramBuffer(wA);
            releaseBigramBuffer(wB);
        }
    }

    private static int[] borrowBigramBuffer() {
        int[] buf = BIGRAM_BUFFER_POOL.poll();
        if (buf != null && buf.length == ZeroAllocationTokenizer.MAX_BIGRAMS_CAP) {
            return buf;
        }
        return new int[ZeroAllocationTokenizer.MAX_BIGRAMS_CAP];
    }

    private static void releaseBigramBuffer(int[] buf) {
        if (buf != null && buf.length == ZeroAllocationTokenizer.MAX_BIGRAMS_CAP) {
            BIGRAM_BUFFER_POOL.offer(buf);
        }
    }

    private static String pickCanonical(String rawA, String rawB, String prepA, String prepB) {
        String ua = prepA.isBlank() ? rawA.strip() : prepA;
        String ub = prepB.isBlank() ? rawB.strip() : prepB;
        if (ua.isBlank()) {
            return ub;
        }
        if (ub.isBlank()) {
            return ua;
        }
        return ua.compareTo(ub) <= 0 ? ua : ub;
    }
}
