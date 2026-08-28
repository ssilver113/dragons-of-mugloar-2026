package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;

/**
 * One ad as a {@link BoardSource} produces it, before the board gives it an expiry.
 *
 * <p>The cipher is chosen here rather than at serialisation time because it is a property of the
 * ad the upstream posts, not of the response.
 */
record GeneratedAd(String adId, String message, int reward, Probability probability, AdCipher cipher) {
}
