package com.mugloar.dragons.ads;

import java.util.Set;

/**
 * An ad after decoding and scoring — the shape both the UI and the solver work with, so a human
 * and the bot are always looking at the same numbers.
 *
 * <p>Left deliberately unsorted: ranking, filtering and re-ranking under a different risk posture
 * are the frontend's job.
 *
 * @param adId               decoded, and the value that goes to {@code /solve}
 * @param message            decoded ad text
 * @param probabilityLabel   decoded label as the API spelled it, for display
 * @param probability        that label parsed, or {@link Probability#UNRECOGNISED}
 * @param successProbability {@code P(success | label, reward, level)}
 * @param expectedValue      {@code reward × successProbability}, in gold
 */
public record EnrichedAd(
        String adId,
        String message,
        int reward,
        int expiresIn,
        boolean encrypted,
        String probabilityLabel,
        Probability probability,
        double successProbability,
        double expectedValue,
        Set<AdFlag> flags) {

    public boolean worthAttempting() {
        return !flags.contains(AdFlag.NEVER_ATTEMPT) && !flags.contains(AdFlag.UNREADABLE);
    }
}
