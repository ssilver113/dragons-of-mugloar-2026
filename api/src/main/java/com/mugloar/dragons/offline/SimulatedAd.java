package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.mugloar.dto.AdResponse;

/**
 * An ad sitting on the offline board, holding its plaintext.
 *
 * <p>Encoding happens on the way out, in {@link #toWire()}, so the board can always be searched by
 * the id a client would have had to decode. Posting the encoded id therefore misses, which is the
 * upstream's own 400 and the trap the whole solver had to be built around.
 */
record SimulatedAd(
        String adId,
        String message,
        int reward,
        Probability probability,
        AdCipher cipher,
        int expiresIn) {

    static SimulatedAd fresh(GeneratedAd generated, int lifetime) {
        return new SimulatedAd(
                generated.adId(),
                generated.message(),
                generated.reward(),
                generated.probability(),
                generated.cipher(),
                lifetime);
    }

    SimulatedAd aged() {
        return new SimulatedAd(adId, message, reward, probability, cipher, expiresIn - 1);
    }

    boolean expired() {
        return expiresIn <= 0;
    }

    AdResponse toWire() {
        return new AdResponse(
                cipher.encode(adId),
                cipher.encode(message),
                reward,
                expiresIn,
                cipher.code(),
                cipher.encode(probability.label()));
    }
}
