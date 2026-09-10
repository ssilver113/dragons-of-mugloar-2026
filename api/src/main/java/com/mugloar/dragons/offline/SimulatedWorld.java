package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.mugloar.exception.InvalidActionException;
import com.mugloar.dragons.shop.ItemEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * One simulated game: the board, the purse and the turn clock.
 *
 * <p>Reproduces what the upstream was measured doing, including the parts easy to get wrong: a
 * turn is spent by solving, buying <em>or failing to buy</em>, and investigating, all of which age
 * the whole board; a failed ad leaves the board; a rejected purchase is a success-looking response
 * with {@code shoppingSuccess=false}; at zero lives every later call is refused.
 *
 * <p>Reputation stays at zero. The exploration measured its turn cost and never established that
 * the figures move, so a drift would put a fact into the game nobody has seen.
 *
 * <p>A solve is drawn against {@link SuccessModel}, the estimator the solver scores with, so a run
 * here measures the solver's arithmetic and not its judgement. Tuning belongs against the live API.
 */
class SimulatedWorld {

    private static final String SOLVED = "You successfully solved the mission!";
    private static final String FAILED = "You failed on the mission!";
    private static final String DEFEATED = "You were defeated on your last mission!";

    private final String gameId;
    private final RandomGenerator rng;
    private final BoardSource boards;
    private final SuccessModel model;
    private final OfflineProperties properties;
    private final List<SimulatedAd> board = new ArrayList<>();

    private int lives;
    private int gold;
    private int level;
    private int score;
    private int turn;

    SimulatedWorld(
            String gameId,
            RandomGenerator rng,
            BoardSource boards,
            SuccessModel model,
            OfflineProperties properties) {
        this.gameId = gameId;
        this.rng = rng;
        this.boards = boards;
        this.model = model;
        this.properties = properties;
        this.lives = properties.startingLives();
        refill();
    }

    synchronized GameStartedResponse started() {
        return new GameStartedResponse(gameId, lives, gold, level, score, turn);
    }

    synchronized List<AdResponse> ads() {
        requireAlive();
        return board.stream().map(SimulatedAd::toWire).toList();
    }

    synchronized List<ShopItemResponse> shopItems() {
        requireAlive();
        return OfflineShop.STOCK;
    }

    synchronized SolveResponse solve(String adId) {
        requireAlive();
        SimulatedAd ad = take(adId);
        if (ad == null) {
            // Unknown, already attempted, or still encoded — all three are the upstream's 400.
            throw new InvalidActionException("No such ad on the board: " + adId);
        }

        boolean success = rng.nextDouble() < model.estimate(ad.probability(), ad.reward(), level);
        if (success) {
            gold += ad.reward();
            score += ad.reward();
        } else {
            lives--;
        }
        spendTurn();

        String message = success ? SOLVED : lives <= 0 ? DEFEATED : FAILED;
        return new SolveResponse(success, lives, gold, score, turn, message);
    }

    synchronized PurchaseResponse buy(String itemId) {
        requireAlive();
        ShopItemResponse item = OfflineShop.find(itemId);
        boolean affordable = item != null && gold >= item.cost();
        if (affordable) {
            gold -= item.cost();
            ItemEffect effect = ItemEffect.forCost(item.cost());
            lives += effect.lives();
            level += effect.levels();
        }
        // The turn goes whether or not the purchase did.
        spendTurn();
        return new PurchaseResponse(affordable, gold, lives, level, turn);
    }

    synchronized ReputationResponse investigate() {
        requireAlive();
        spendTurn();
        return new ReputationResponse(0, 0, 0);
    }

    private void requireAlive() {
        if (lives <= 0) {
            throw new GameOverException("The game is over");
        }
    }

    /** Removes the ad by its plaintext id, which is the only id the upstream accepts. */
    private SimulatedAd take(String adId) {
        for (int i = 0; i < board.size(); i++) {
            if (board.get(i).adId().equals(adId)) {
                return board.remove(i);
            }
        }
        return null;
    }

    private void spendTurn() {
        turn++;
        board.replaceAll(SimulatedAd::aged);
        board.removeIf(SimulatedAd::expired);
        if (lives > 0) {
            refill();
        }
    }

    private void refill() {
        while (board.size() < boards.boardSize()) {
            board.add(SimulatedAd.fresh(
                    boards.nextAd(level, turn, rng), properties.adLifetime()));
        }
    }
}
