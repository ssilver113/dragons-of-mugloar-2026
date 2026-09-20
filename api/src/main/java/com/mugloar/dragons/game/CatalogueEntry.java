package com.mugloar.dragons.game;

/**
 * One line of the shop as the session remembers it.
 *
 * <p>Lives here rather than in the shop package so the ledger stays on the session without
 * inverting the dependency between the two. It carries what cannot be derived again — an id, a
 * display name and a price; an item's effect is a pure function of its cost, so the shop rebuilds
 * that half rather than storing it twice.
 */
public record CatalogueEntry(String id, String name, int cost) {
}
