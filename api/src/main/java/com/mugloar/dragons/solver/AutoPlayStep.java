package com.mugloar.dragons.solver;

import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.game.Reputation;

/**
 * One turn of auto-play: what was decided, and what it left behind.
 *
 * @param succeeded  whether the move did what it was for. A solved mission, a purchase the shop
 *                   honoured, or a pass, which cannot fail
 * @param message    the upstream sentence for a solve, null for anything else
 * @param reputation the standing an investigation returned, null for every other move — it is the
 *                   only move that reports any
 */
public record AutoPlayStep(
        GameState game, Decision decision, boolean succeeded, String message, Reputation reputation) {

    public static AutoPlayStep of(GameState game, Decision decision, boolean succeeded, String message) {
        return new AutoPlayStep(game, decision, succeeded, message, null);
    }
}
