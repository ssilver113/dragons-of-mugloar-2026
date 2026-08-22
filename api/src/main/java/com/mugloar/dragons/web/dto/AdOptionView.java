package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.solver.AdOption;
import com.mugloar.dragons.solver.Verdict;

/**
 * One ad as the solver saw it when it decided. Carries the whole ad rather than an id, because the
 * ad may well have expired off the board by the time anyone reads the log entry.
 *
 * @param score {@code reward × p − lifeCost × (1 − p)}, in gold. Below zero means the expected
 *              reward did not cover the risk to a life at the lives held that turn
 */
public record AdOptionView(
        String adId,
        String message,
        int reward,
        int expiresIn,
        String probability,
        String probabilityTier,
        double successProbability,
        double score,
        Verdict verdict) {

    public static AdOptionView from(AdOption option) {
        return new AdOptionView(
                option.adId(),
                option.message(),
                option.reward(),
                option.expiresIn(),
                option.probability(),
                option.probabilityTier(),
                Rounding.estimate(option.successProbability()),
                Rounding.estimate(option.score()),
                option.verdict());
    }
}
