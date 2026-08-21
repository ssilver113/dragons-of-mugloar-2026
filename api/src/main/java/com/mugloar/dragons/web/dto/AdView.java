package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.ads.AdFlag;
import com.mugloar.dragons.ads.EnrichedAd;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * A decoded, scored ad. {@code adId} is always the decoded form — the encoded one is a 400 waiting
 * to happen and never leaves the server.
 *
 * <p>Both estimates are rounded on the way out: four decimals is far beyond what the underlying
 * measurements support, and it keeps the payload readable.
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
                round(ad.successProbability()),
                round(ad.expectedValue()),
                ad.flags());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
