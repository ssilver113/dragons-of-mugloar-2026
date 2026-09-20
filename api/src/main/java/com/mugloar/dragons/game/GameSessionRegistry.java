package com.mugloar.dragons.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * In-memory session store, keyed by game id. No database: nothing here needs to outlive the
 * process, and games are short.
 *
 * <p>Eviction is lazy — checked on lookup, swept on registration — rather than scheduled. It costs
 * no background thread, and the only thing an unswept entry occupies is a little memory.
 *
 * <p>It is also how a service reaches a session at all. {@link #exclusively} and {@link #takeTurn}
 * look the game up, take its lock and read its state inside it, in that order, so there is no
 * expression in a service that yields a session without the lock already held. Read outside it,
 * the state is a snapshot from before an upstream round trip, and the same omission has been
 * found twice.
 */
@Component
public class GameSessionRegistry {

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public GameSessionRegistry(GameProperties properties, Clock clock) {
        this.ttl = properties.sessionTtl();
        this.clock = clock;
    }

    public GameSession register(GameState state) {
        evictExpired();
        GameSession session = new GameSession(state, clock.instant());
        sessions.put(state.gameId(), session);
        return session;
    }

    public GameSession require(String gameId) {
        GameSession session = sessions.get(gameId);
        if (session == null) {
            throw new SessionExpiredException(gameId);
        }
        Instant now = clock.instant();
        if (isExpired(session, now)) {
            sessions.remove(gameId, session);
            throw new SessionExpiredException(gameId);
        }
        session.touch(now);
        return session;
    }

    /**
     * Run an action against a running game, holding its lock and reading its state inside it.
     *
     * <p>Free of a turn, not free of the lock: a listing writes the ledger a turn's guards read,
     * and it publishes the state alongside its own answer.
     */
    public <T> T exclusively(String gameId, BiFunction<GameSession, GameState, T> action) {
        GameSession session = require(gameId);
        return session.exclusively(() -> action.apply(session, session.requireRunning()));
    }

    /** The same exclusion, named for the case that pays for it. See {@link GameSession#takeTurn}. */
    public <T> T takeTurn(String gameId, BiFunction<GameSession, GameState, T> action) {
        GameSession session = require(gameId);
        return session.takeTurn(() -> action.apply(session, session.requireRunning()));
    }

    private void evictExpired() {
        Instant now = clock.instant();
        sessions.values().removeIf(session -> isExpired(session, now));
    }

    /** How many sessions are held. Nothing in the app asks; the eviction tests do. */
    int size() {
        return sessions.size();
    }

    private boolean isExpired(GameSession session, Instant now) {
        return session.lastAccessed().plus(ttl).isBefore(now);
    }
}
