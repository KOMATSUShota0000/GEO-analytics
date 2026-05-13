package com.geo.analytics.application.service;

import com.geo.analytics.application.dto.ResolutionResult;
import com.geo.analytics.domain.matching.StringBigramTokenizer;
import com.geo.analytics.domain.model.ResolvableEntity;
import com.geo.analytics.domain.service.EntityNormalizer;
import com.geo.analytics.domain.service.EntityResolutionBlockingService;
import com.geo.analytics.domain.service.JapaneseNlpService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public final class EntityResolutionService {

    /** キュー廃止・DeepSeek撤去に伴いバンプ。 */
    public static final String CALCULATION_VERSION = "ER_PIPELINE_V2_GEMINI_SLIM";

    private static final double LEXICAL_DICE_HIGH = 0.85;

    private final JapaneseNlpService japaneseNlpService;
    private final EntityResolutionBlockingService entityResolutionBlockingService;

    public EntityResolutionService(
            JapaneseNlpService japaneseNlpService, EntityResolutionBlockingService entityResolutionBlockingService) {
        this.japaneseNlpService = japaneseNlpService;
        this.entityResolutionBlockingService = entityResolutionBlockingService;
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
            return separate(rawA, rawB, ha, hb);
        }
        double dice = lexicalDiceOnNormalized(nfa, nfb);
        if (dice >= LEXICAL_DICE_HIGH) {
            return new ResolutionResult.Merged(
                    pickCanonical(rawA, rawB, prepA, prepB),
                    2,
                    dice,
                    CALCULATION_VERSION);
        }
        return separate(rawA, rawB, ha, hb);
    }

    private ResolutionResult separate(String left, String right, String ha, String hb) {
        return new ResolutionResult.NotMerged(left, right, ha, hb, CALCULATION_VERSION);
    }

    private static double lexicalDiceOnNormalized(String nfa, String nfb) {
        if (nfa.isBlank() && nfb.isBlank()) {
            return 1.0;
        }
        return StringBigramTokenizer.diceCoefficient(nfa, nfb);
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
