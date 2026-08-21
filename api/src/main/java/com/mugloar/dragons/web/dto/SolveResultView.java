package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.game.SolveOutcome;

/** Response of {@code POST /api/games/{gameId}/ads/{adId}/solve}. */
public record SolveResultView(GameView game, String adId, boolean success, String message) {

    public static SolveResultView from(SolveOutcome outcome) {
        return new SolveResultView(
                GameView.from(outcome.game()),
                outcome.adId(),
                outcome.success(),
                outcome.message());
    }
}
