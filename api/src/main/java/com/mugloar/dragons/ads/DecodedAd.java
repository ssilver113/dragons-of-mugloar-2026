package com.mugloar.dragons.ads;

import com.mugloar.dragons.mugloar.dto.AdResponse;

/**
 * An ad with its cipher removed. The {@code adId} here is the one that must be posted to
 * {@code /solve} — sending the encoded form returns 400 (D24), so the encoded form never leaves
 * this package.
 */
public record DecodedAd(
        String adId,
        String message,
        String probabilityLabel,
        int reward,
        int expiresIn,
        boolean encrypted) {

    public static DecodedAd from(AdResponse wire) {
        AdCipher cipher = AdCipher.forCode(wire.encrypted());
        return new DecodedAd(
                cipher.decode(wire.adId()),
                cipher.decode(wire.message()),
                cipher.decode(wire.probability()),
                wire.reward(),
                wire.expiresIn(),
                cipher != AdCipher.NONE);
    }
}
