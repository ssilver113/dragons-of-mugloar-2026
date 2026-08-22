package com.mugloar.dragons.bench;

import java.util.List;

/**
 * The shape of a run's scores.
 *
 * <p>The assignment asks for a program that <em>reliably</em> reaches a score, which is a claim
 * about a distribution rather than about one game. {@link #below} is the number that answers it;
 * the percentiles say how much room there is above the bar.
 *
 * <p>Percentiles are nearest-rank on the sorted scores — no interpolation, so every figure printed
 * is a score some game actually reached.
 */
record ScoreDistribution(
        int games,
        int min,
        int p25,
        int median,
        int p75,
        int p95,
        int max,
        double mean,
        int below,
        int threshold) {

    static final ScoreDistribution EMPTY =
            new ScoreDistribution(0, 0, 0, 0, 0, 0, 0, 0.0, 0, 0);

    static ScoreDistribution of(List<Integer> scores, int threshold) {
        if (scores.isEmpty()) {
            return EMPTY;
        }
        List<Integer> sorted = scores.stream().sorted().toList();
        return new ScoreDistribution(
                sorted.size(),
                sorted.getFirst(),
                percentile(sorted, 25),
                percentile(sorted, 50),
                percentile(sorted, 75),
                percentile(sorted, 95),
                sorted.getLast(),
                sorted.stream().mapToInt(Integer::intValue).average().orElseThrow(),
                (int) sorted.stream().filter(score -> score < threshold).count(),
                threshold);
    }

    double belowShare() {
        return games == 0 ? 0.0 : (double) below / games;
    }

    private static int percentile(List<Integer> sorted, int percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
        return sorted.get(Math.clamp(rank - 1, 0, sorted.size() - 1));
    }
}
