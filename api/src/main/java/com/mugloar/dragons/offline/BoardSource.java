package com.mugloar.dragons.offline;

import java.util.random.RandomGenerator;

/**
 * Where the offline board's ads come from.
 *
 * <p>The seam exists because the two halves of the simulation have very different standing. The
 * turn, expiry, gold and life bookkeeping in {@link SimulatedWorld} is measured behaviour, recorded
 * in {@code docs/api-findings.md}. What the board *offers* — how many ads, at what rewards, under
 * which labels, as level and turn climb — was never measured, because only the ads the solver chose
 * were ever recorded. So the generator is the part that is allowed to be wrong, and it is isolated
 * so it can be replaced by one sampling recorded boards without touching the rest.
 */
interface BoardSource {

    GeneratedAd nextAd(int level, int turn, RandomGenerator rng);

    /** How many ads the board holds. A property of what deals them, not of the world. */
    int boardSize();
}
