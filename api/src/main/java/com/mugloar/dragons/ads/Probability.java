package com.mugloar.dragons.ads;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The eleven probability labels the API uses, plus a fallback for anything unrecognised.
 *
 * <p>The prior lives on the {@link Tier}, not on the constant, because the labels within a tier are
 * statistically indistinguishable at the sample size we have — an earlier, smaller sample ranked
 * them differently and that ordering did not survive more data. Trusting a strict per-label ranking
 * would be reading noise (D21).
 *
 * <p>Measured rates from the recon run (n = 992), for traceability:
 * Sure thing 0.88 / Walk in the park 0.88 / Piece of cake 0.87 · Quite likely 0.76 / Hmmm.... 0.72 ·
 * Gamble 0.44 / Risky 0.37 · Rather detrimental 0.27 / Playing with fire 0.21 ·
 * Suicide mission 0.05 / Impossible 0.00.
 *
 * <p>The prior is only half the estimate — see {@link SuccessModel}, which corrects it for the ad's
 * reward relative to the dragon's level.
 */
public enum Probability {

    SURE_THING("Sure thing", Tier.SAFE),
    WALK_IN_THE_PARK("Walk in the park", Tier.SAFE),
    PIECE_OF_CAKE("Piece of cake", Tier.SAFE),
    QUITE_LIKELY("Quite likely", Tier.FAVOURABLE),
    HMMM("Hmmm....", Tier.FAVOURABLE),
    GAMBLE("Gamble", Tier.EVEN),
    RISKY("Risky", Tier.EVEN),
    RATHER_DETRIMENTAL("Rather detrimental", Tier.POOR),
    PLAYING_WITH_FIRE("Playing with fire", Tier.POOR),
    SUICIDE_MISSION("Suicide mission", Tier.DOOMED),
    IMPOSSIBLE("Impossible", Tier.DOOMED),

    /** A label the API produced that we have never seen. Scored at zero so it is never chosen. */
    UNRECOGNISED("", Tier.UNKNOWN);

    /** Equivalence classes of labels, each carrying the prior shared by its members. */
    public enum Tier {
        SAFE(0.87),
        FAVOURABLE(0.74),
        EVEN(0.41),
        POOR(0.23),
        DOOMED(0.03),
        UNKNOWN(0.0);

        private final double prior;

        Tier(double prior) {
            this.prior = prior;
        }

        public double prior() {
            return prior;
        }
    }

    private static final Map<String, Probability> BY_LABEL = Arrays.stream(values())
            .filter(p -> p != UNRECOGNISED)
            .collect(Collectors.toUnmodifiableMap(p -> normalise(p.label), Function.identity()));

    private final String label;
    private final Tier tier;

    Probability(String label, Tier tier) {
        this.label = label;
        this.tier = tier;
    }

    /** The label exactly as the API spells it. */
    public String label() {
        return label;
    }

    public Tier tier() {
        return tier;
    }

    public double prior() {
        return tier.prior();
    }

    /**
     * Never worth a turn at any reward: {@code Impossible} went 0 for 300 and
     * {@code Suicide mission} 7 for 128. Excluded outright rather than down-weighted (D23).
     */
    public boolean neverAttempt() {
        return tier == Tier.DOOMED || tier == Tier.UNKNOWN;
    }

    /** Unknown labels map to {@link #UNRECOGNISED} rather than throwing: one odd ad must not sink the board. */
    public static Probability fromLabel(String label) {
        if (label == null) {
            return UNRECOGNISED;
        }
        return BY_LABEL.getOrDefault(normalise(label), UNRECOGNISED);
    }

    private static String normalise(String label) {
        return label.strip().toLowerCase(Locale.ROOT);
    }
}
