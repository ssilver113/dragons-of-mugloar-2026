package com.mugloar.dragons.solver;

import com.mugloar.dragons.game.AdBoard;
import com.mugloar.dragons.game.GameService;
import com.mugloar.dragons.game.SolveOutcome;
import com.mugloar.dragons.shop.PurchaseOutcome;
import com.mugloar.dragons.shop.ShopCatalogue;
import com.mugloar.dragons.shop.ShopService;
import org.springframework.stereotype.Service;

/**
 * Advances exactly one turn: read the board and the shop, ask the strategy, carry out its move.
 *
 * <p>It acts through the existing services rather than the upstream client, so the guards, the
 * session ledgers and the turn accounting are the same ones the human player goes through. The bot
 * gets no private path to the game.
 *
 * <p>One turn per call and no loop of its own — running to completion is the client repeating this,
 * which is what keeps a run abortable and lets the UI show every turn as it happens. Reading the
 * board and the shop costs nothing, so both are refreshed before every decision.
 */
@Service
public class AutoPlayService {

    private final GameService games;
    private final ShopService shop;
    private final Strategy strategy;

    public AutoPlayService(GameService games, ShopService shop, Strategy strategy) {
        this.games = games;
        this.shop = shop;
        this.strategy = strategy;
    }

    public AutoPlayStep step(String gameId) {
        AdBoard board = games.listAds(gameId);
        ShopCatalogue catalogue = shop.listItems(gameId);
        Decision decision = strategy.decide(board.game(), board.ads(), catalogue.items());

        return switch (decision.move().type()) {
            case SOLVE_AD -> {
                SolveOutcome outcome = games.solve(gameId, decision.move().targetId());
                yield new AutoPlayStep(
                        outcome.game(), decision, outcome.success(), outcome.message());
            }
            case BUY_ITEM -> {
                PurchaseOutcome outcome = shop.buy(gameId, decision.move().targetId());
                yield new AutoPlayStep(outcome.game(), decision, outcome.success(), null);
            }
            case INVESTIGATE_REPUTATION ->
                    new AutoPlayStep(games.passTurn(gameId), decision, true, null);
        };
    }
}
