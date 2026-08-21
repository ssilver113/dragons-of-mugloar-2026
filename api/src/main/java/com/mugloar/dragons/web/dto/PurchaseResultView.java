package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.shop.PurchaseOutcome;

/**
 * Response of {@code POST /api/games/{gameId}/shop/{itemId}/buy}.
 *
 * <p>A refusal by the shop is a 200 here, not an error: the turn was spent, so the client needs
 * the new state more than it needs a failure status. Only refusals we caught before spending
 * anything come back as errors.
 */
public record PurchaseResultView(GameView game, String itemId, boolean success) {

    public static PurchaseResultView from(PurchaseOutcome outcome) {
        return new PurchaseResultView(
                GameView.from(outcome.game()), outcome.itemId(), outcome.success());
    }
}
