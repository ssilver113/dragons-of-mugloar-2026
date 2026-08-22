package com.mugloar.dragons.bench;

/**
 * One solve, as the state stood before it and as it turned out. The row the success model is
 * refitted from, so every input the model takes is recorded next to the outcome it predicted.
 *
 * @param estimate what the model gave the attempt beforehand
 * @param score    the strategy's risk-adjusted score in gold, kept so a refit can see what the
 *                 policy would have done rather than only what the model believed
 */
record SolveAttempt(
        String gameId,
        int turn,
        int level,
        int lives,
        int gold,
        int reward,
        String label,
        String tier,
        int expiresIn,
        double estimate,
        double score,
        boolean success) {
}
