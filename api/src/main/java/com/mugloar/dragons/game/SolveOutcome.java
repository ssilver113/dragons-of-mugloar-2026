package com.mugloar.dragons.game;

/** The result of one solve attempt, with the state it left behind. */
public record SolveOutcome(GameState game, String adId, boolean success, String message) {
}
