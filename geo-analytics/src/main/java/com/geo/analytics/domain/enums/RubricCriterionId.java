package com.geo.analytics.domain.enums;

import java.util.Arrays;
import java.util.List;

public enum RubricCriterionId {
    DIRECT_ANSWER_FIRST(Source.LLM, 5.0d),
    ATOMIC_FACTS(Source.LLM, 5.0d),
    SOLUTION_SCENARIOS(Source.LLM, 5.0d),
    VERIFIABLE_AUTHORITY(Source.LLM, 5.0d),
    FAQ_PRESENCE(Source.LLM, 5.0d),
    NUMBERED_PROCESS_FLOW(Source.LLM, 5.0d),
    ENTITY_BIOGRAPHY(Source.LLM, 5.0d),
    LOCAL_CONTEXT(Source.LLM, 5.0d),
    PRICE_AND_CONSTRAINTS(Source.LLM, 5.0d),
    EXTERNAL_CITATIONS(Source.LLM, 5.0d),
    MACHINE_READABILITY_SIGNAL(Source.SYSTEM, 25.0d),
    MEO_TRUST_SCORE(Source.MEO, 25.0d);

    public enum Source {
        LLM,
        SYSTEM,
        MEO
    }

    private final Source source;
    private final double maxScore;

    RubricCriterionId(Source source, double maxScore) {
        this.source = source;
        this.maxScore = maxScore;
    }

    public Source source() {
        return source;
    }

    public double maxScore() {
        return maxScore;
    }

    public static List<RubricCriterionId> llmCriteria() {
        return Arrays.stream(values()).filter(c -> c.source == Source.LLM).toList();
    }

    public static List<RubricCriterionId> systemCriteria() {
        return Arrays.stream(values()).filter(c -> c.source == Source.SYSTEM).toList();
    }

    public static List<RubricCriterionId> meoCriteria() {
        return Arrays.stream(values()).filter(c -> c.source == Source.MEO).toList();
    }
}
