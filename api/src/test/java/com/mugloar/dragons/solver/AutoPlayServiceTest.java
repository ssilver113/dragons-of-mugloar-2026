package com.mugloar.dragons.solver;

import com.mugloar.dragons.ads.AdEnricher;
import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.game.GameNotRunningException;
import com.mugloar.dragons.game.GameProperties;
import com.mugloar.dragons.game.GameService;
import com.mugloar.dragons.game.GameSessionRegistry;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.game.SessionExpiredException;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.shop.ItemEffect;
import com.mugloar.dragons.shop.ShopItem;
import com.mugloar.dragons.shop.ShopService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The step wired to the real services, with only the network faked — the point being that the bot
 * goes through the same guards and the same session ledger as the human player.
 */
class AutoPlayServiceTest {

    private static final String GAME_ID = "IeLKvlDb";

    private static final List<ShopItemResponse> SHOP = List.of(
            new ShopItemResponse("hpot", "Healing potion", 50),
            new ShopItemResponse("cs", "Claw Sharpening", 100),
            new ShopItemResponse("ch", "Copper Plate Mail", 300));

    private MugloarClient client;
    private GameSessionRegistry sessions;
    private AutoPlayService autoPlay;

    @BeforeEach
    void setUp() {
        client = mock(MugloarClient.class);
        sessions = new GameSessionRegistry(
                new GameProperties(Duration.ofMinutes(30)), Clock.systemUTC());
        GameService games = new GameService(client, new AdEnricher(SuccessModel.MEASURED), sessions);
        ShopService shop = new ShopService(client, sessions);
        autoPlay = new AutoPlayService(
                games, shop, new RiskAdjustedStrategy(StrategyParameters.DEFAULT));
        when(client.startGame()).thenReturn(new GameStartedResponse(GAME_ID, 3, 0, 0, 0, 0));
        when(client.listShopItems(GAME_ID)).thenReturn(SHOP);
        games.startGame();
    }

    private void gameIs(int lives, int gold, int level, int turn) {
        sessions.require(GAME_ID).setState(new GameState(GAME_ID, lives, gold, level, 400, turn));
    }

    private void boardIs(AdResponse... ads) {
        when(client.listAds(GAME_ID)).thenReturn(List.of(ads));
    }

    private static AdResponse ad(String adId, int reward, String probability) {
        return new AdResponse(adId, "Help someone", reward, 5, null, probability);
    }

    @Test
    void solvesTheChosenAdAndReturnsTheStateItLeftBehind() {
        gameIs(3, 0, 5, 0);
        boardIs(ad("safe", 60, "Piece of cake"), ad("trap", 400, "Piece of cake"));
        when(client.solve(GAME_ID, "safe"))
                .thenReturn(new SolveResponse(true, 3, 60, 460, 1, "You successfully solved the mission!"));

        AutoPlayStep step = autoPlay.step(GAME_ID);

        assertThat(step.decision().move()).isEqualTo(Move.solve("safe"));
        assertThat(step.succeeded()).isTrue();
        assertThat(step.message()).isEqualTo("You successfully solved the mission!");
        assertThat(step.game()).isEqualTo(new GameState(GAME_ID, 3, 60, 5, 460, 1));
    }

    /** Level is carried by the purchase, which is the only response that reports it. */
    @Test
    void buysWhenTheStrategySaysToAndTracksTheLevelItBought() {
        gameIs(3, 120, 0, 0);
        boardIs(ad("safe", 60, "Piece of cake"));
        when(client.buy(GAME_ID, "cs")).thenReturn(new PurchaseResponse(true, 20, 3, 1, 1));

        AutoPlayStep step = autoPlay.step(GAME_ID);

        assertThat(step.decision().move()).isEqualTo(Move.buy("cs"));
        assertThat(step.decision().reason()).isEqualTo(Reason.LEVELLING_BEHIND_TARGET);
        assertThat(step.game()).isEqualTo(new GameState(GAME_ID, 3, 20, 1, 400, 1));
        verify(client, never()).solve(anyString(), anyString());
    }

    /** A shop refusal is still a spent turn, so the step reports it rather than throwing. */
    @Test
    void reportsARefusedPurchaseWithoutLosingTheTurnItCost() {
        gameIs(3, 120, 0, 0);
        boardIs(ad("safe", 60, "Piece of cake"));
        when(client.buy(GAME_ID, "cs")).thenReturn(new PurchaseResponse(false, 120, 3, 0, 1));

        AutoPlayStep step = autoPlay.step(GAME_ID);

        assertThat(step.succeeded()).isFalse();
        assertThat(step.game().turn()).isEqualTo(1);
        assertThat(step.game().gold()).isEqualTo(120);
    }

    /**
     * The pass. Its response carries no state at all, so the turn is applied locally — verified
     * live: the ads aged by one and nothing else moved.
     */
    @Test
    void passesOnADeadBoardItCannotBuyItsWayOutOf() {
        gameIs(3, 20, 5, 0);
        boardIs(ad("hopeless", 900, "Impossible"));
        when(client.investigateReputation(GAME_ID)).thenReturn(new ReputationResponse(0, 0, 0));

        AutoPlayStep step = autoPlay.step(GAME_ID);

        assertThat(step.decision().move().type()).isEqualTo(MoveType.INVESTIGATE_REPUTATION);
        assertThat(step.succeeded()).isTrue();
        assertThat(step.game()).isEqualTo(new GameState(GAME_ID, 3, 20, 5, 400, 1));
        verify(client, never()).solve(anyString(), anyString());
        verify(client, never()).buy(anyString(), anyString());
    }

    @Test
    void refusesToStepAGameThatIsOver() {
        gameIs(0, 200, 5, 40);

        assertThatThrownBy(() -> autoPlay.step(GAME_ID)).isInstanceOf(GameNotRunningException.class);
        verify(client, never()).listAds(anyString());
    }

    @Test
    void refusesToStepAGameWithNoLiveSession() {
        assertThatThrownBy(() -> autoPlay.step("Zm9vYmFy")).isInstanceOf(SessionExpiredException.class);
        verify(client, never()).listAds("Zm9vYmFy");
    }

    /** Reading the board and the shop is free, so both are refreshed before every decision. */
    @Test
    void readsTheBoardAndTheShopBeforeEachDecision() {
        gameIs(3, 0, 5, 0);
        boardIs(ad("safe", 60, "Piece of cake"));
        when(client.solve(GAME_ID, "safe"))
                .thenReturn(new SolveResponse(true, 3, 60, 460, 1, "You successfully solved the mission!"));

        autoPlay.step(GAME_ID);

        verify(client).listAds(GAME_ID);
        verify(client).listShopItems(GAME_ID);
    }

    /**
     * The overload something playing hundreds of games uses. Prices never move within a game, so a
     * caller holding the catalogue skips a third of the upstream calls; the board is still read
     * every turn, because that does move.
     */
    @Test
    void takesTheSameTurnAgainstACatalogueTheCallerAlreadyHolds() {
        gameIs(3, 0, 5, 0);
        boardIs(ad("safe", 60, "Piece of cake"));
        when(client.solve(GAME_ID, "safe"))
                .thenReturn(new SolveResponse(true, 3, 60, 460, 1, "You successfully solved the mission!"));
        List<ShopItem> catalogue = List.of(
                new ShopItem("hpot", "Healing potion", 50, ItemEffect.EXTRA_LIFE),
                new ShopItem("cs", "Claw Sharpening", 100, ItemEffect.LEVEL_UP),
                new ShopItem("ch", "Copper Plate Mail", 300, ItemEffect.DOUBLE_LEVEL_UP));

        AutoPlayStep step = autoPlay.step(GAME_ID, catalogue);

        assertThat(step.decision().move()).isEqualTo(Move.solve("safe"));
        assertThat(step.decision().items()).extracting(ItemOption::itemId)
                .containsExactly("hpot", "cs", "ch");
        assertThat(step.game()).isEqualTo(new GameState(GAME_ID, 3, 60, 5, 460, 1));
        verify(client).listAds(GAME_ID);
        verify(client, never()).listShopItems(GAME_ID);
    }
}
