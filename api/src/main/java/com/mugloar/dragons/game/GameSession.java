package com.mugloar.dragons.game;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * One game's server-side state.
 *
 * <p>Guarded at two levels, because a player can have the auto-play loop and the UI acting on the
 * same game at once. The accessors are synchronised, which makes each field access atomic. That
 * alone is not enough: an action reads the state, calls upstream and writes it back, so
 * {@link #takeTurn} exists to hold the whole of one against the others.
 *
 * <p>Its ledgers let an impossible action be refused without an upstream call: the last board's
 * ids, every id already attempted, and the shop's prices. Ids and prices rather than the objects,
 * because an ad's remaining life changes every turn and a stale copy would be worse than none.
 */
public class GameSession {

    private final ReentrantLock turn = new ReentrantLock();
    private final Set<String> attemptedAdIds = new HashSet<>();
    private GameState state;
    private Set<String> boardAdIds;
    private Map<String, Integer> itemCosts;
    private Instant lastAccessed;

    GameSession(GameState state, Instant now) {
        this.state = state;
        this.lastAccessed = now;
    }

    /**
     * Runs a whole turn against this game with no other turn on it in flight, upstream call
     * included. Turns are strictly sequential in the game itself — a turn is the scarce resource,
     * and two of them at once is not a thing the rules describe — so serialising them here costs
     * nothing real. Two interleaved would lose one update, and the player would have been charged
     * a turn that left no trace.
     *
     * <p>A lock rather than the monitor above: this one is held across a network call, and the
     * client's own connect and read timeouts are what bound it. Reentrant, so a service taking a
     * turn may call another that does the same.
     */
    public <T> T takeTurn(Supplier<T> action) {
        turn.lock();
        try {
            return action.get();
        } finally {
            turn.unlock();
        }
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
