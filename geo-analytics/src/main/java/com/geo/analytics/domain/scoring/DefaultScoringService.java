package com.geo.analytics.domain.scoring;

import java.lang.StrictMath;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DefaultScoringService implements ScoringService {

    private static final double WEIGHT_SUM_TOLERANCE = 1e-9;

    @Override
    public ScoreBreakdown calculateFinalScore(double aiScore, double meoScore, double machineScore) {
        List<WeightedScoreInput> inputs = List.of(
                new WeightedScoreInput(aiScore, 0.5), new WeightedScoreInput(meoScore, 0.25),
                new WeightedScoreInput(machineScore, 0.25));
        double geo = calculateWeightedAverage(inputs);
        return new ScoreBreakdown(aiScore, meoScore, machineScore, geo);
    }

    @Override
    public double calculateWeightedAverage(List<WeightedScoreInput> inputs) {
        Objects.requireNonNull(inputs);
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException();
        }
        double sumWeights = 0.0;
        for (WeightedScoreInput in : inputs) {
            requireFinite(in.value());
            requireFinite(in.weight());
            if (in.weight() < 0.0 || in.value() < 0.0 || in.value() > 100.0) {
                throw new IllegalArgumentException();
            }
            sumWeights = StrictMath.fma(1.0, in.weight(), sumWeights);
        }
        if (StrictMath.abs(StrictMath.fma(sumWeights, 1.0, -1.0)) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException();
        }
        double acc = 0.0;
        for (WeightedScoreInput in : inputs) {
            acc = StrictMath.fma(in.value(), in.weight(), acc);
        }
        return Math.clamp(acc, 0.0, 100.0);
    }

    private static void requireFinite(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            throw new IllegalArgumentException();
        }
    }
}
