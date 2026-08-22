package com.mugloar.dragons.bench;

import com.mugloar.dragons.ads.AdEnricher;
import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.game.GameProperties;
import com.mugloar.dragons.game.GameService;
import com.mugloar.dragons.game.GameSessionRegistry;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import com.mugloar.dragons.shop.ShopService;
import com.mugloar.dragons.solver.AutoPlayService;
import com.mugloar.dragons.solver.RiskAdjustedStrategy;
import com.mugloar.dragons.solver.StrategyParameters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The benchmarked game wired to the real services with only the network faked, for the same reason
 * the auto-play step is: what the harness measures has to be the bot as shipped, going through the
 * same guards and the same session ledger.
 */
class BenchmarkGameTest {

    @TempDir
    Path directory;

    private FakeUpstream upstream;
    private AttemptLog attempts;

    @BeforeEach
    void setUp() throws IOException {
        upstream = new FakeUpstream();
        attempts = AttemptLog.open(directory.resolve("attempts.csv"));
    }

    @Test
    void playsUntilLivesRunOut() {
        GameResult result = play(properties(400, 40));

        assertThat(result.outcome()).isEqualTo(GameOutcome.DIED);
        assertThat(result.turns()).isEqualTo(3);
        assertThat(upstream.lives).isZero();
    }

    /** The saving the harness exists to take: prices are static, so one fetch covers every turn. */
    @Test
    void readsTheShopOncePerGameRatherThanOncePerTurn() {
        play(properties(400, 40));

        assertThat(upstream.shopListings).isEqualTo(1);
    }

    @Test
    void recordsEverySolveAgainstTheStateItWasScoredAgainst() throws IOException {
        play(properties(400, 40));

        List<String> rows = Files.readAllLines(attempts.file());

        assertThat(rows).hasSize(4);
        assertThat(rows.getFirst()).startsWith("game,turn,level,lives,gold,reward");
        assertThat(rows.get(1)).startsWith("g1,0,0,3,0,60,\"Piece of cake\",SAFE,");
        assertThat(rows.get(2)).startsWith("g1,1,0,2,0,60,");
        assertThat(rows.get(3)).startsWith("g1,2,0,1,0,60,");
        assertThat(rows).allSatisfy(row -> assertThat(row).doesNotEndWith(",1"));
        assertThat(attempts.rows()).isEqualTo(3);
    }

    @Test
    void stopsAtTheTurnCapWhenNothingIsKillingTheDragon() {
        upstream.solvesSucceed = true;

        GameResult result = play(properties(5, 40));

        assertThat(result.outcome()).isEqualTo(GameOutcome.TURN_CAP);
        assertThat(result.turns()).isEqualTo(5);
        assertThat(result.score()).isPositive();
    }

    /**
     * A pass costs a turn and cannot cost a life, so a broke dragon facing a board it will not
     * touch has no move that ends the game. The cap is what stops the run rather than the game.
     */
    @Test
    void givesUpOnAGameThatOnlyEverPasses() {
        upstream.label = "Impossible";

        GameResult result = play(properties(400, 3));

        assertThat(result.outcome()).isEqualTo(GameOutcome.STALLED);
        assertThat(result.turns()).isEqualTo(3);
    }

    @Test
    void abandonsAGameTheUpstreamStopsAnswering() {
        upstream.onListAds = new MugloarUnavailableException("timed out", null);
        upstream.listAdsBeforeFailing = 1;

        GameResult result = play(properties(400, 40));

        assertThat(result.outcome()).isEqualTo(GameOutcome.ABORTED);
        assertThat(result.note()).contains("timed out");
        assertThat(result.counted()).isFalse();
    }

    /** A 410 is the game ending, not the harness failing, so the score still counts. */
    @Test
    void countsAGameTheUpstreamDeclaresOverAsADeath() {
        upstream.onSolve = new GameOverException("Game Over");

        GameResult result = play(properties(400, 40));

        assertThat(result.outcome()).isEqualTo(GameOutcome.DIED);
        assertThat(result.counted()).isTrue();
    }

    private GameResult play(BenchmarkProperties properties) {
        GameSessionRegistry sessions = new GameSessionRegistry(
                new GameProperties(Duration.ofMinutes(30)), Clock.systemUTC());
        GameService games =
                new GameService(upstream, new AdEnricher(SuccessModel.MEASURED), sessions);
        ShopService shop = new ShopService(upstream, sessions);
        AutoPlayService autoPlay = new AutoPlayService(
                games, shop, new RiskAdjustedStrategy(StrategyParameters.DEFAULT));

        return new BenchmarkGame(games, shop, autoPlay, properties).play(attempts);
    }

    private BenchmarkProperties properties(int maxTurns, int maxConsecutivePasses) {
        return new BenchmarkProperties(
                1, 1, Duration.ofMillis(1), Duration.ofSeconds(1), 1.5,
                maxTurns, maxConsecutivePasses, Duration.ZERO,
                directory.toString(), "test", 1000,
                StrategyParameters.DEFAULT, SuccessModel.MEASURED);
    }

    /**
     * A whole upstream in one class: it keeps the state, ages the turn on every action that should
     * cost one, and hands out a fresh pair of ad ids each turn the way the real board does.
     */
    private static final class FakeUpstream implements MugloarClient {

        private static final List<ShopItemResponse> SHOP = List.of(
                new ShopItemResponse("hpot", "Healing potion", 50),
                new ShopItemResponse("cs", "Claw Sharpening", 100),
                new ShopItemResponse("ch", "Copper Plate Mail", 300));

        private int lives = 3;
        private int gold;
        private int level;
        private int score;
        private int turn;
        private int shopListings;

        private int reward = 60;
        private String label = "Piece of cake";
        private boolean solvesSucceed;
        private RuntimeException onSolve;
        private RuntimeException onListAds;
        private int listAdsBeforeFailing = Integer.MAX_VALUE;

        @Override
        public GameStartedResponse startGame() {
            return new GameStartedResponse("g1", lives, gold, level, score, turn);
        }

        @Override
        public List<AdResponse> listAds(String gameId) {
            if (--listAdsBeforeFailing < 0 && onListAds != null) {
                throw onListAds;
            }
            return List.of(
                    new AdResponse("ad-" + turn + "-a", "Escort a merchant", reward, 5, null, label),
                    new AdResponse("ad-" + turn + "-b", "Chase off a rat", reward, 3, null, label));
        }

        @Override
        public SolveResponse solve(String gameId, String adId) {
            if (onSolve != null) {
                throw onSolve;
            }
            turn++;
            if (solvesSucceed) {
                gold += reward;
                score += reward;
            } else {
                lives--;
            }
            return new SolveResponse(solvesSucceed, lives, gold, score, turn,
                    solvesSucceed ? "Success! The merchant is grateful." : "You failed.");
        }

        @Override
        public List<ShopItemResponse> listShopItems(String gameId) {
            shopListings++;
            return SHOP;
        }

        @Override
        public PurchaseResponse buy(String gameId, String itemId) {
            turn++;
            int cost = SHOP.stream()
                    .filter(item -> item.id().equals(itemId))
                    .findFirst()
                    .orElseThrow()
                    .cost();
            gold -= cost;
            switch (cost) {
                case 50 -> lives++;
                case 100 -> level += 1;
                default -> level += 2;
            }
            return new PurchaseResponse(true, gold, lives, level, turn);
        }

        @Override
        public ReputationResponse investigateReputation(String gameId) {
            turn++;
            return new ReputationResponse(0.0, 0.0, 0.0);
        }
    }
}
