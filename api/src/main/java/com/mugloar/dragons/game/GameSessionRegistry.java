package com.mugloar.dragons.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory session store, keyed by game id. No database: nothing here needs to outlive the
 * process, and games are short.
 *
 * <p>Eviction is lazy — checked on lookup, swept on registration — rather than scheduled. It costs
 * no background thread, and the only thing an unswept entry occupies is a little memory.
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

    private void evictExpired() {
        Instant now = clock.instant();
        sessions.values().removeIf(session -> isExpired(session, now));
    }

    public int size() {
        return sessions.size();
    }

    private boolean isExpired(GameSession session, Instant now) {
        return session.lastAccessed().plus(ttl).isBefore(now);
    }
}
