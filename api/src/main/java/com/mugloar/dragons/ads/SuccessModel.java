package com.mugloar.dragons.ads;

/**
 * Estimates {@code P(success | label, reward, level)}.
 *
 * <p>The label alone is not a probability. The top three labels measure 0.93 below 100 gold, 0.50
 * at 100–150 and <b>0.00 at 150–200</b> — all at level 0. The same 150–200 band runs 0.50 at levels
 * 2–6 and 0.94 at level 12. A label is a difficulty rating relative to your dragon, so the estimate
 * is the tier prior scaled by how far the reward overshoots what that level can comfortably
 * handle.
 *
 * <p>The correction is a logistic in the reward, so the estimate stays smooth and monotonic and
 * the whole curve scales with level. At a width of 0.066 of the ceiling the game behaves much more
 * like a threshold than like a slope.
 *
 * <p>{@link #MEASURED} is fitted by maximum likelihood over 2,486 recorded solve attempts plus the
 * hand-driven exploration's table, which is the only evidence covering rich ads at a low level.
 * Calibration over that data, mean prediction against observed rate:
 * <pre>
 *   by level     &lt;4  0.79 vs 0.83   ·  4-8  0.79 vs 0.79  ·  8-12  0.69 vs 0.69
 *                12-18  0.57 vs 0.58  ·  18+  0.29 vs 0.28
 *   by richness  under half the old ceiling 0.84 vs 0.84  ·  past 1.2x it  0.10 vs 0.08
 * </pre>
 *
 * <p>A record rather than a static utility so a sweep can swap in a refitted instance without
 * touching the strategy.
 *
 * @param ceilingBase     safe reward at level 0
 * @param ceilingPerLevel how much each level adds to that ceiling
 * @param midpointFactor  where the 50% point sits, as a multiple of the ceiling
 * @param softnessFactor  logistic width, as a fraction of the ceiling
 */
public record SuccessModel(
        double ceilingBase,
        double ceilingPerLevel,
        double midpointFactor,
        double softnessFactor) {

    /** Fitted to measured outcomes, not derived from a rule. Refit whenever a longer run exists. */
    public static final SuccessModel MEASURED = new SuccessModel(112.32, 8.05, 1.25, 0.0655);

    public SuccessModel {
        if (ceilingBase <= 0 || softnessFactor <= 0 || midpointFactor <= 0) {
            throw new IllegalArgumentException("SuccessModel parameters must be positive");
        }
        // Zero is a legitimate fit; a negative slope would shrink the ceiling as the dragon grew.
        if (ceilingPerLevel < 0) {
            throw new IllegalArgumentException("SuccessModel ceilingPerLevel must not be negative");
        }
    }

    /**
     * The highest reward this level handles comfortably — roughly 112 at level 0, 145 at level 4,
     * 209 at level 12. Also the threshold behind {@link AdFlag#OUT_OF_LEAGUE}, so a flag and a
     * score can never disagree about what is too rich for this dragon.
     */
    public int safeRewardCeiling(int level) {
        return (int) Math.round(ceilingBase + ceilingPerLevel * Math.max(0, level));
    }

    public double estimate(Probability probability, int reward, int level) {
        if (probability.neverAttempt()) {
            return 0.0;
        }
        return probability.prior() * feasibility(reward, level);
    }

    /** How much of the label's prior survives this reward at this level, in (0, 1). */
    public double feasibility(int reward, int level) {
        double ceiling = safeRewardCeiling(level);
        double midpoint = ceiling * midpointFactor;
        double softness = ceiling * softnessFactor;
        return 1.0 / (1.0 + Math.exp((reward - midpoint) / softness));
    }
}
