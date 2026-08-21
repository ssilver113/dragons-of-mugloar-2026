package com.mugloar.dragons.game;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One game's server-side state.
 *
 * <p>Synchronised because a player can have the auto-play loop and the UI acting on the same game
 * at once, and both read-modify-write the state.
 *
 * <p>Alongside the state it keeps ledgers, so an action that cannot succeed is refused without
 * spending an upstream call: the ids from the board we last fetched, every id we have already
 * attempted, and what the shop charges. Ids and prices rather than the objects themselves — an
 * ad's remaining life changes with every turn, and a cached copy that quietly disagreed with the
 * board would be worse than no cache at all.
 */
public class GameSession {

    private final Set<String> attemptedAdIds = new HashSet<>();
    private GameState state;
    private Set<String> boardAdIds;
    private Map<String, Integer> itemCosts;
    private Instant lastAccessed;

    GameSession(GameState state, Instant now) {
        this.state = state;
        this.lastAccessed = now;
    }

    public synchronized GameState state() {
        return state;
    }

    public synchronized void setState(GameState state) {
        this.state = state;
    }

    /** The state, provided the game can still act on it. */
    public synchronized GameState requireRunning() {
        if (state.finished()) {
            throw new GameNotRunningException(state.gameId());
        }
        return state;
    }

    public synchronized void recordBoard(Collection<String> adIds) {
        this.boardAdIds = Set.copyOf(adIds);
    }

    /**
     * False only when we can prove the ad is unsolvable: already attempted, or absent from a board
     * we have actually seen. Before the first fetch nothing is known, so the upstream decides.
     */
    public synchronized boolean isKnownSolvable(String adId) {
        if (attemptedAdIds.contains(adId)) {
            return false;
        }
        return boardAdIds == null || boardAdIds.contains(adId);
    }

    public synchronized void recordAttempt(String adId) {
        attemptedAdIds.add(adId);
    }

    public synchronized void recordShop(Map<String, Integer> costs) {
        this.itemCosts = Map.copyOf(costs);
    }

    public synchronized boolean knowsShop() {
        return itemCosts != null;
    }

    public synchronized OptionalInt itemCost(String itemId) {
        Integer cost = itemCosts == null ? null : itemCosts.get(itemId);
        return cost == null ? OptionalInt.empty() : OptionalInt.of(cost);
    }

    synchronized Instant lastAccessed() {
        return lastAccessed;
    }

    synchronized void touch(Instant now) {
        this.lastAccessed = now;
    }
}
