package com.mugloar.dragons.solver;

import com.mugloar.dragons.game.AdBoard;
import com.mugloar.dragons.game.GameService;
import com.mugloar.dragons.game.PassOutcome;
import com.mugloar.dragons.game.SolveOutcome;
import com.mugloar.dragons.shop.PurchaseOutcome;
import com.mugloar.dragons.shop.ShopCatalogue;
import com.mugloar.dragons.shop.ShopItem;
import com.mugloar.dragons.shop.ShopService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Advances exactly one turn: read the board and the shop, ask the strategy, carry out its move.
 *
 * <p>Through the existing services rather than the upstream client, so the bot gets no private
 * path to the game. One turn per call and no loop of its own, which is what keeps a run abortable
 * and lets the UI show every turn. Reading the board and the shop costs no turn, but it does cost
 * an upstream call against something that rate-limits on burst, so only the board is re-read.
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

    /**
     * One turn, reading the shop from the session's ledger once the game has seen it. The board is
     * still refreshed every turn: unlike prices, it moves.
     */
    public AutoPlayStep step(String gameId) {
        AdBoard board = games.listAds(gameId);
        ShopCatalogue catalogue = shop.knownCatalogue(gameId);
        return act(gameId, board, catalogue.items());
    }

    /**
     * One turn against a catalogue the caller already holds, for a run that plays hundreds of them
     * and would rather not ask the server for the same list each time.
     */
    public AutoPlayStep step(String gameId, List<ShopItem> catalogue) {
        return act(gameId, games.listAds(gameId), catalogue);
    }

    private AutoPlayStep act(String gameId, AdBoard board, List<ShopItem> catalogue) {
        Decision decision = strategy.decide(board.game(), board.ads(), catalogue);

        return switch (decision.move().type()) {
            case SOLVE_AD -> {
                SolveOutcome outcome = games.solve(gameId, decision.move().targetId());
                yield AutoPlayStep.of(
                        outcome.game(), decision, outcome.success(), outcome.message());
            }
            case BUY_ITEM -> {
                PurchaseOutcome outcome = shop.buy(gameId, decision.move().targetId());
                yield AutoPlayStep.of(outcome.game(), decision, outcome.success(), null);
            }
            case INVESTIGATE_REPUTATION -> {
                PassOutcome outcome = games.passTurn(gameId);
                yield new AutoPlayStep(
                        outcome.game(), decision, true, null, outcome.reputation());
            }
        };
    }
}
