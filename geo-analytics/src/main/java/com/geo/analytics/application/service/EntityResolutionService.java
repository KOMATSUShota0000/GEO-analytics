package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ResolutionResult;
import com.geo.analytics.domain.entity.UnresolvedEntityQueueEntity;
import com.geo.analytics.domain.model.ResolvableEntity;
import com.geo.analytics.domain.service.EntityResolutionBlockingService;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.JapaneseNlpService;
import com.geo.analytics.infrastructure.ai.DeepSeekAdapter;
import com.geo.analytics.infrastructure.repository.UnresolvedEntityQueueRepository;
import com.geo.analytics.infrastructure.tenant.DefaultTenantIds;
import com.geo.analytics.infrastructure.tenant.TenantContext;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public final class EntityResolutionService {
    public static final String CALCULATION_VERSION = "ER_PIPELINE_V1";
    private static final double JARO_HIGH = 0.85;
    private static final double JARO_LOW = 0.70;
    private static final double LLM_CONFIDENCE_MIN = 0.90;
    private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();
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
        var rawA = labelA != null ? labelA : "";
        var rawB = labelB != null ? labelB : "";
        var prepA = EntityNormalizer.prepareForSudachi(rawA);
        var prepB = EntityNormalizer.prepareForSudachi(rawB);
        var nfa = japaneseNlpService.normalizedForm(prepA);
        var nfb = japaneseNlpService.normalizedForm(prepB);
        if (!nfa.isBlank() && nfa.equals(nfb)) {
            return new ResolutionResult.Merged(
                pickCanonical(rawA, rawB, prepA, prepB),
                1,
                1.0,
                CALCULATION_VERSION);
        }
        var ha = entityResolutionBlockingService.blockingHashSha256(rawA);
        var hb = entityResolutionBlockingService.blockingHashSha256(rawB);
        if (!ha.equals(hb)) {
            return persistPending(rawA, rawB, ha, hb);
        }
        var jw = jaroOnNormalized(nfa, nfb);
        if (jw >= JARO_HIGH) {
            return new ResolutionResult.Merged(
                pickCanonical(rawA, rawB, prepA, prepB),
                2,
                jw,
                CALCULATION_VERSION);
        }
        if (jw >= JARO_LOW && jw < JARO_HIGH) {
            var judgment = deepSeekAdapter.judgeEntityIdentityBlocking(prepA, prepB);
            if (judgment.sameEntity() && judgment.confidence() >= LLM_CONFIDENCE_MIN) {
                return new ResolutionResult.Merged(
                    pickCanonical(rawA, rawB, prepA, prepB),
                    3,
                    jw,
                    CALCULATION_VERSION);
            }
        }
        return persistPending(rawA, rawB, ha, hb);
    }

    private ResolutionResult persistPending(String left, String right, String ha, String hb) {
        try {
            var row = new UnresolvedEntityQueueEntity();
            var tid = TenantContext.getTenantId();
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

    private static double jaroOnNormalized(String nfa, String nfb) {
        if (nfa.isBlank() && nfb.isBlank()) {
            return 1.0;
        }
        return JARO_WINKLER.apply(nfa, nfb);
    }

    private static String pickCanonical(String rawA, String rawB, String prepA, String prepB) {
        var ua = prepA.isBlank() ? rawA.strip() : prepA;
        var ub = prepB.isBlank() ? rawB.strip() : prepB;
        if (ua.isBlank()) {
            return ub;
        }
        if (ub.isBlank()) {
            return ua;
        }
        return ua.compareTo(ub) <= 0 ? ua : ub;
    }
}
