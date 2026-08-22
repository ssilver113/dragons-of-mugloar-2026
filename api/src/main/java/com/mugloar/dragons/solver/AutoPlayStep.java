package com.mugloar.dragons.solver;

import com.mugloar.dragons.game.GameState;

/**
 * One turn of auto-play: what was decided, and what it left behind.
 *
 * @param succeeded whether the move did what it was for. A solved mission, a purchase the shop
 *                  honoured, or a pass, which cannot fail
 * @param message   the upstream sentence for a solve, null for anything else
 */
public record AutoPlayStep(GameState game, Decision decision, boolean succeeded, String message) {
}
