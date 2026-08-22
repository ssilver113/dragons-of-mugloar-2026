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
 * <p>The correction is a logistic in the reward, which keeps the estimate smooth and monotonic.
 * Its midpoint sits {@link #midpointFactor} above {@link #safeRewardCeiling(int)} and its width is
 * {@link #softnessFactor} of that ceiling, so the whole curve scales with level rather than
 * flattening out at high level.
 *
 * <p>{@link #MEASURED} is fitted by maximum likelihood over 2,486 solve attempts recorded by the
 * benchmark harness, plus the hand-driven exploration's own table at its measured weight — the
 * exploration is the only evidence covering rich ads at a low level, because the solver's own
 * estimate keeps it out of that corner. The tool is {@code tools/fit-success-model.py}.
 *
 * <p>Two things changed against the first fit, which had only the exploration to go on and covered
 * levels 0 to 12 over turns under 50. The ceiling now starts <em>higher</em> and climbs
 * <em>slower</em> — 112 rather than 100 at level 0, and 8 per level rather than 12 — so the old
 * shape was mildly pessimistic early and badly optimistic late, which is what long runs kept
 * showing. And the curve is far sharper: the width fell from 0.18 of the ceiling to 0.066. Within a
 * band roughly a tenth of the ceiling wide the estimate goes from most of the prior to nearly
 * nothing, so the game behaves much more like a threshold than like a slope.
 *
 * <p>Calibration over the fitted data, mean prediction against observed rate:
 * <pre>
 *   by level     &lt;4  0.79 vs 0.83   ·  4-8  0.79 vs 0.79  ·  8-12  0.69 vs 0.69
 *                12-18  0.57 vs 0.58  ·  18+  0.29 vs 0.28
 *   by richness  under half the old ceiling 0.84 vs 0.84  ·  past 1.2x it  0.10 vs 0.08
 * </pre>
 * The band that mattered is the last one: the previous fit gave those ads 0.29 and they came in at
 * 0.08 across 265 attempts.
 *
 * <p>This is a record rather than a static utility so a sweep can fit the four parameters against
 * benchmark data and swap in a refitted instance without touching the strategy.
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
        // Zero is a legitimate fit — a game whose difficulty did not scale with level — but a
        // negative slope would shrink the ceiling as the dragon grew, inverting every estimate.
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
