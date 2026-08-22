package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.ads.AdFlag;
import com.mugloar.dragons.ads.EnrichedAd;
import java.util.Set;

/**
 * A decoded, scored ad. {@code adId} is always the decoded form — the encoded one is a 400 waiting
 * to happen and never leaves the server.
 */
public record AdView(
        String adId,
        String message,
        int reward,
        int expiresIn,
        boolean encrypted,
        String probability,
        String probabilityTier,
        double successProbability,
        double expectedValue,
        Set<AdFlag> flags) {

    public static AdView from(EnrichedAd ad) {
        return new AdView(
                ad.adId(),
                ad.message(),
                ad.reward(),
                ad.expiresIn(),
                ad.encrypted(),
                ad.probabilityLabel(),
                ad.probability().tier().name(),
                Rounding.estimate(ad.successProbability()),
                Rounding.estimate(ad.expectedValue()),
                ad.flags());
    }
}
