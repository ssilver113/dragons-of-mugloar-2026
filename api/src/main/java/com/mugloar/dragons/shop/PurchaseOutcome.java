package com.mugloar.dragons.shop;

import com.mugloar.dragons.game.GameState;

/**
 * The result of one purchase attempt, with the state it left behind.
 *
 * <p>{@code success} can be false on a perfectly healthy response: the upstream reports a refused
 * purchase as a 200 and charges the turn anyway, so the state here is still worth applying.
 */
public record PurchaseOutcome(GameState game, String itemId, boolean success) {
}
