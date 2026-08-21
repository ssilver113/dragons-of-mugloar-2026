package com.mugloar.dragons.ads;

import com.mugloar.dragons.mugloar.dto.AdResponse;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Turns raw board entries into scored {@link EnrichedAd}s for a dragon at a given level. */
@Component
public class AdEnricher {

    private static final Logger log = LoggerFactory.getLogger(AdEnricher.class);

    private final SuccessModel successModel;

    public AdEnricher(SuccessModel successModel) {
        this.successModel = successModel;
    }

    public List<EnrichedAd> enrich(List<AdResponse> board, int level) {
        return board.stream().map(ad -> enrich(ad, level)).toList();
    }

    public EnrichedAd enrich(AdResponse wire, int level) {
        DecodedAd decoded;
        try {
            decoded = DecodedAd.from(wire);
        } catch (AdDecodingException e) {
            // Show it, greyed and unscored, rather than hiding a board entry the player can see
            // exists. It can never be selected, because an undecoded adId would 400 anyway.
            log.warn("Could not decode ad {}: {}", wire.adId(), e.getMessage());
            return unreadable(wire);
        }

        Probability probability = Probability.fromLabel(decoded.probabilityLabel());
        double success = successModel.estimate(probability, decoded.reward(), level);

        Set<AdFlag> flags = EnumSet.noneOf(AdFlag.class);
        if (decoded.expiresIn() <= 1) {
            flags.add(AdFlag.EXPIRING_NEXT_TURN);
        }
        if (decoded.reward() > successModel.safeRewardCeiling(level)) {
            flags.add(AdFlag.OUT_OF_LEAGUE);
        }
        if (probability == Probability.UNRECOGNISED) {
            flags.add(AdFlag.UNREADABLE);
        } else if (probability.neverAttempt()) {
            flags.add(AdFlag.NEVER_ATTEMPT);
        }

        return new EnrichedAd(
                decoded.adId(),
                decoded.message(),
                decoded.reward(),
                decoded.expiresIn(),
                decoded.encrypted(),
                decoded.probabilityLabel(),
                probability,
                success,
                decoded.reward() * success,
                flags);
    }

    private static EnrichedAd unreadable(AdResponse wire) {
        Set<AdFlag> flags = EnumSet.of(AdFlag.UNREADABLE);
        if (wire.expiresIn() <= 1) {
            flags.add(AdFlag.EXPIRING_NEXT_TURN);
        }
        return new EnrichedAd(
                wire.adId(),
                wire.message(),
                wire.reward(),
                wire.expiresIn(),
                wire.encrypted() != null,
                wire.probability(),
                Probability.UNRECOGNISED,
                0.0,
                0.0,
                flags);
    }
}
