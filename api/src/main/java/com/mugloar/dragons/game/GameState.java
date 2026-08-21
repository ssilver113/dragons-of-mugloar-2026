package com.mugloar.dragons.game;

import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;

/**
 * Everything we know about a game in flight.
 *
 * <p>{@code level} is tracked here rather than read back, because the API reports it only on
 * {@code /game/start} and {@code /shop/buy} — {@code /solve} omits it entirely. Since the whole ad
 * score depends on level, losing track of it silently corrupts every estimate, which is why a lost
 * session is refused outright rather than resumed at a guessed level.
 */
public record GameState(String gameId, int lives, int gold, int level, int score, int turn) {

    public static GameState from(GameStartedResponse started) {
        return new GameState(
                started.gameId(),
                started.lives(),
                started.gold(),
                started.level(),
                started.score(),
                started.turn());
    }

    public boolean finished() {
        return lives <= 0;
    }

    /** Merges a solve result, carrying {@code level} forward because the response does not report it. */
    public GameState afterSolve(SolveResponse solved) {
        return new GameState(gameId, solved.lives(), solved.gold(), level, solved.score(), solved.turn());
    }
}
