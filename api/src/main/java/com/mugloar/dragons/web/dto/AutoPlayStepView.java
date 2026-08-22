package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.solver.AutoPlayStep;

/**
 * Response of {@code POST /api/games/{gameId}/autoplay/step}.
 *
 * <p>The board is not included: listing it costs no turn, so the client refetches it exactly as it
 * does after a manual move, and the decision already carries the board as the solver saw it.
 */
public record AutoPlayStepView(
        GameView game, DecisionView decision, boolean succeeded, String message) {

    public static AutoPlayStepView from(AutoPlayStep step) {
        return new AutoPlayStepView(
                GameView.from(step.game()),
                DecisionView.from(step.decision()),
                step.succeeded(),
                step.message());
    }
}
