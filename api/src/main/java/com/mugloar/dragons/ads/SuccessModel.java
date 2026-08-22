package com.mugloar.dragons.ads;

/**
 * Estimates {@code P(success | label, reward, level)}.
 *
 * <p>The label alone is not a probability. Recon measured the top three labels at 0.93 below 100
 * gold, 0.50 at 100–150 and <b>0.00 at 150–200</b> — all at level 0. The same 150–200 band runs
 * 0.50 at levels 2–6 and 0.94 at level 12. A label is a difficulty rating relative to your dragon,
 * so the estimate is the tier prior scaled by how far the reward overshoots what that level can
 * comfortably handle.
 *
 * <p>The correction is a logistic in the reward, which keeps the estimate smooth and monotonic —
 * a cliff would make the UI's what-if ranking jump and would throw away the measured 0.50 band.
 * Its midpoint sits {@link #midpointFactor} above {@link #safeRewardCeiling(int)} and its width is
 * {@link #softnessFactor} of that ceiling, so the whole curve scales with level rather than
 * flattening out at high level.
 *
 * <p>Fit of {@link #MEASURED} against the recon tables (estimate vs observed, top-tier labels):
 * <pre>
 *   level  0, reward  50 → 0.86 vs 0.93      level  4, reward 125 → 0.78 vs 0.88
 *   level  0, reward 125 → 0.44 vs 0.50      level  4, reward 175 → 0.51 vs 0.50
 *   level  0, reward 175 → 0.05 vs 0.00      level 12, reward 175 → 0.83 vs 0.94
 * </pre>
 *
 * <p>This is a record rather than a static utility so Phase 10 can sweep the four parameters
 * against benchmark data and swap in a refitted instance without touching the strategy.
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

    /** Fitted to the recon data. The starting point for Phase 10, not a derived rule. */
    public static final SuccessModel MEASURED = new SuccessModel(100.0, 12.0, 1.25, 0.18);

    public SuccessModel {
        if (ceilingBase <= 0 || softnessFactor <= 0 || midpointFactor <= 0) {
            throw new IllegalArgumentException("SuccessModel parameters must be positive");
        }
    }

    /**
     * The highest reward this level handles comfortably — roughly 100 at level 0, 150 at level 4,
     * 250 at level 12. Also the threshold behind {@link AdFlag#OUT_OF_LEAGUE}.
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
