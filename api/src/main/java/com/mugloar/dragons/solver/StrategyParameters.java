package com.mugloar.dragons.solver;

/**
 * The knobs the strategy trades on. Chosen, not measured — recon settled how the game behaves, not
 * how to play it, so these are starting points for the benchmark to sweep rather than facts.
 *
 * <p>A record for the same reason the success model is one: a sweep can inject a different instance
 * without touching the strategy.
 *
 * @param lifeValueGold        what a life is worth in gold when lives are plentiful. Divided by the
 *                             lives in hand, so the last one is the dearest and the risk posture
 *                             falls out of one number instead of a table of thresholds
 * @param potionThresholdLives buy a healing potion at or below this many lives
 * @param targetLevelBase      level the dragon should reach as soon as it can afford to
 * @param targetLevelPerTurn   how much that target grows each turn. The board's reward scale climbs
 *                             whether or not the dragon does, so a static target decays into
 *                             failure — recon watched {@code Piece of cake} fall from 0.95 to 0.04
 *                             over fifty turns at a fixed level 0
 * @param dearTierGoldFloor    gold above which the two-level items are preferred. Below it gold is
 *                             the binding constraint and the one-level items buy 1.5× the level per
 *                             gold; above it turns bind instead, and the dear tier buys 2× per turn
 */
public record StrategyParameters(
        double lifeValueGold,
        int potionThresholdLives,
        int targetLevelBase,
        double targetLevelPerTurn,
        int dearTierGoldFloor) {

    public static final StrategyParameters DEFAULT =
            new StrategyParameters(300.0, 1, 2, 0.2, 600);

    public StrategyParameters {
        if (lifeValueGold <= 0 || targetLevelPerTurn < 0 || dearTierGoldFloor < 0) {
            throw new IllegalArgumentException("StrategyParameters must be positive");
        }
    }

    /** What losing a life costs right now. Scarcity, not sentiment: the last life is worth the most. */
    public double lifeCost(int lives) {
        return lifeValueGold / Math.max(1, lives);
    }

    public int targetLevel(int turn) {
        return targetLevelBase + (int) (targetLevelPerTurn * Math.max(0, turn));
    }
}
