package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.game.PassOutcome;

/** Response of {@code POST /api/games/{gameId}/investigate}. The turn is spent either way. */
public record InvestigationView(GameView game, ReputationView reputation) {

    public static InvestigationView from(PassOutcome outcome) {
        return new InvestigationView(
                GameView.from(outcome.game()), ReputationView.from(outcome.reputation()));
    }
}
