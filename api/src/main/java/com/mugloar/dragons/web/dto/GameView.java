package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.game.GameState;

/**
 * The complete game state, returned by every endpoint that touches it, so the client never has to
 * reconstruct it from partial updates and an optimistic rollback is a straight state swap.
 */
public record GameView(
        String gameId,
        int lives,
        int gold,
        int level,
        int score,
        int turn,
        boolean finished) {

    public static GameView from(GameState state) {
        return new GameView(
                state.gameId(),
                state.lives(),
                state.gold(),
                state.level(),
                state.score(),
                state.turn(),
                state.finished());
    }
}
