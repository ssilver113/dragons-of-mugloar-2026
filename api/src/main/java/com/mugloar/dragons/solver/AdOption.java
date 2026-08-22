package com.mugloar.dragons.solver;

/**
 * One ad as the strategy saw it, with the verdict it earned.
 *
 * <p>Self-contained on purpose. By the time a log entry is read the ad may have expired off the
 * board, so an entry that only carried an id would be unreadable a few turns later.
 *
 * @param score {@code reward × p − lifeCost × (1 − p)}, in gold. Negative means the expected reward
 *              does not cover the risk to a life at the number of lives held at the time
 */
public record AdOption(
        String adId,
        String message,
        int reward,
        int expiresIn,
        String probability,
        String probabilityTier,
        double successProbability,
        double score,
        Verdict verdict) {
}
