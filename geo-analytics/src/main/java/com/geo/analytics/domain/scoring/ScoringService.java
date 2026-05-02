package com.geo.analytics.domain.scoring;

import java.util.List;

public interface ScoringService {

    ScoreBreakdown calculateFinalScore(double aiScore, double meoScore, double machineScore);

    double calculateWeightedAverage(List<WeightedScoreInput> inputs);
}
