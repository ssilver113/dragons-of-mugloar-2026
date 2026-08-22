package com.mugloar.dragons.bench;

import com.mugloar.dragons.game.GameNotRunningException;
import com.mugloar.dragons.game.GameService;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.shop.ShopItem;
import com.mugloar.dragons.shop.ShopService;
import com.mugloar.dragons.solver.AutoPlayService;
import com.mugloar.dragons.solver.AutoPlayStep;
import com.mugloar.dragons.solver.AdOption;
import com.mugloar.dragons.solver.MoveType;
import com.mugloar.dragons.solver.Verdict;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Plays one game from start to death, taking the same turns the auto-play UI would.
 *
 * <p>It goes through {@link AutoPlayService} rather than the strategy directly, so what the
 * benchmark measures is the bot as shipped — the same guards, the same session ledger, the same
 * turn accounting. The one thing it does differently is read the shop once instead of once a turn,
 * which is a saving in upstream calls and changes no decision, because prices are fixed.
 *
 * <p>Two bounds stop a game that will not end on its own: a turn cap, and a limit on consecutive
 * passes. A pass costs a turn and cannot cost a life, so a dragon with no gold facing a board it
 * cannot afford to touch has no move that ends the game.
 */
@Component
@Profile("bench")
class BenchmarkGame {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkGame.class);

    private final GameService games;
    private final ShopService shop;
    private final AutoPlayService autoPlay;
    private final BenchmarkProperties properties;

    BenchmarkGame(
            GameService games,
            ShopService shop,
            AutoPlayService autoPlay,
            BenchmarkProperties properties) {
        this.games = games;
        this.shop = shop;
        this.autoPlay = autoPlay;
        this.properties = properties;
    }

    GameResult play(AttemptLog attempts) {
        GameState state = games.startGame();
        // Also primes the session's price ledger, so purchases do not fetch the shop again.
        List<ShopItem> catalogue = shop.listItems(state.gameId()).items();
        int passes = 0;

        while (!state.finished()) {
            if (state.turn() >= properties.maxTurns()) {
                return result(state, GameOutcome.TURN_CAP);
            }
            if (passes >= properties.maxConsecutivePasses()) {
                return result(state, GameOutcome.STALLED);
            }

            AutoPlayStep step;
            try {
                step = autoPlay.step(state.gameId(), catalogue);
            } catch (GameNotRunningException | GameOverException e) {
                // The game ended; our copy of the state had simply not caught up with it.
                return result(state, GameOutcome.DIED);
            } catch (RuntimeException e) {
                // Broad on purpose: a game lost to an unexpected failure has to be counted as
                // abandoned, or an unattended run quietly reports fewer games than it played.
                log.warn("Game {} abandoned at turn {}", state.gameId(), state.turn(), e);
                return GameResult.aborted(
                        state.gameId(), state.score(), state.turn(), state.level(), e.toString());
            }

            recordSolve(attempts, state, step);
            passes = step.decision().move().type() == MoveType.INVESTIGATE_REPUTATION
                    ? passes + 1
                    : 0;
            state = step.game();
        }
        return result(state, GameOutcome.DIED);
    }

    /** The state is the one the ad was scored against, so estimate and outcome line up. */
    private static void recordSolve(AttemptLog attempts, GameState before, AutoPlayStep step) {
        if (step.decision().move().type() != MoveType.SOLVE_AD) {
            return;
        }
        step.decision().ads().stream()
                .filter(ad -> ad.verdict() == Verdict.CHOSEN)
                .findFirst()
                .map(ad -> attempt(before, ad, step.succeeded()))
                .ifPresent(attempts::record);
    }

    private static SolveAttempt attempt(GameState before, AdOption ad, boolean succeeded) {
        return new SolveAttempt(
                before.gameId(),
                before.turn(),
                before.level(),
                before.lives(),
                before.gold(),
                ad.reward(),
                ad.probability(),
                ad.probabilityTier(),
                ad.expiresIn(),
                ad.successProbability(),
                ad.score(),
                succeeded);
    }

    private static GameResult result(GameState state, GameOutcome outcome) {
        return new GameResult(
                state.gameId(), state.score(), state.turn(), state.level(), outcome, null);
    }
}
