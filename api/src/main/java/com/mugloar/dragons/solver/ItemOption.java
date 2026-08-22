package com.mugloar.dragons.solver;

/**
 * One purchase the strategy weighed, with the verdict it earned.
 *
 * <p>Only the candidates matter — the cheapest potion and one item from each level tier. Listing
 * all eleven would repeat the same three offers with different flavour names.
 */
public record ItemOption(
        String itemId,
        String name,
        int cost,
        int livesGained,
        int levelsGained,
        Verdict verdict) {
}
