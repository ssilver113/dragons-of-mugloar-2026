package com.mugloar.dragons.game;

/** The result of spending a turn on an investigation: a turn gone, and the standing it bought. */
public record PassOutcome(GameState game, Reputation reputation) {
}
