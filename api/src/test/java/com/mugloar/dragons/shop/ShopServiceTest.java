package com.mugloar.dragons.shop;

import com.mugloar.dragons.game.GameNotRunningException;
import com.mugloar.dragons.game.GameProperties;
import com.mugloar.dragons.game.GameSessionRegistry;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.game.SessionExpiredException;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopServiceTest {

    private static final String GAME_ID = "IeLKvlDb";

    private static final List<ShopItemResponse> CATALOGUE = List.of(
            new ShopItemResponse("hpot", "Healing potion", 50),
            new ShopItemResponse("cs", "Claw Sharpening", 100),
            new ShopItemResponse("wingpotmax", "Potion of Awesome Wings", 300));

    private MugloarClient client;
    private GameSessionRegistry sessions;
    private ShopService service;

    @BeforeEach
    void setUp() {
        client = mock(MugloarClient.class);
        sessions = new GameSessionRegistry(
                new GameProperties(Duration.ofMinutes(30)), Clock.systemUTC());
        service = new ShopService(client, sessions);
        when(client.listShopItems(GAME_ID)).thenReturn(CATALOGUE);
    }

    private void gameWith(int gold) {
        sessions.register(new GameState(GAME_ID, 3, gold, 2, 400, 9));
    }

    @Test
    void listsTheShopWithTheEffectMeasuredForEachPrice() {
        gameWith(120);

        ShopCatalogue catalogue = service.listItems(GAME_ID);

        assertThat(catalogue.game().gold()).isEqualTo(120);
        assertThat(catalogue.items())
                .extracting(ShopItem::id, ShopItem::cost, ShopItem::effect)
                .containsExactly(
                        tuple("hpot", 50, ItemEffect.EXTRA_LIFE),
                        tuple("cs", 100, ItemEffect.LEVEL_UP),
                        tuple("wingpotmax", 300, ItemEffect.DOUBLE_LEVEL_UP));
    }

    @Test
    void buyingAppliesTheStateTheShopReports() {
        gameWith(120);
        service.listItems(GAME_ID);
        when(client.buy(GAME_ID, "cs")).thenReturn(new PurchaseResponse(true, 20, 3, 3, 10));

        PurchaseOutcome outcome = service.buy(GAME_ID, "cs");

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.game()).isEqualTo(new GameState(GAME_ID, 3, 20, 3, 400, 10));
        assertThat(sessions.require(GAME_ID).requireRunning().level()).isEqualTo(3);
    }

    @Test
    void keepsScoreOnAPurchaseBecauseItCountsGoldEarnedNotHeld() {
        gameWith(120);
        service.listItems(GAME_ID);
        when(client.buy(GAME_ID, "cs")).thenReturn(new PurchaseResponse(true, 20, 3, 3, 10));

        assertThat(service.buy(GAME_ID, "cs").game().score()).isEqualTo(400);
    }

    @Test
    void fetchesThePricesItselfWhenThePlayerNeverBrowsed() {
        gameWith(120);
        when(client.buy(GAME_ID, "cs")).thenReturn(new PurchaseResponse(true, 20, 3, 3, 10));

        assertThat(service.buy(GAME_ID, "cs").success()).isTrue();
        verify(client).listShopItems(GAME_ID);
    }

    @Test
    void doesNotRefetchThePricesOnceItKnowsThem() {
        gameWith(400);
        service.listItems(GAME_ID);
        when(client.buy(anyString(), anyString())).thenReturn(new PurchaseResponse(true, 100, 3, 4, 10));

        service.buy(GAME_ID, "wingpotmax");

        verify(client, times(1)).listShopItems(GAME_ID);
    }

    @Test
    void refusesAnItemTheShopDoesNotStockWithoutSpendingATurn() {
        gameWith(400);
        service.listItems(GAME_ID);

        assertThatThrownBy(() -> service.buy(GAME_ID, "sword"))
                .isInstanceOf(ItemNotAvailableException.class);
        verify(client, never()).buy(anyString(), anyString());
    }

    @Test
    void refusesAnItemThePurseCannotCoverWithoutSpendingATurn() {
        gameWith(299);
        service.listItems(GAME_ID);

        assertThatThrownBy(() -> service.buy(GAME_ID, "wingpotmax"))
                .isInstanceOf(InsufficientGoldException.class)
                .hasMessageContaining("299");
        verify(client, never()).buy(anyString(), anyString());
    }

    @Test
    void allowsAPurchaseThatSpendsThePurseExactlyDry() {
        gameWith(300);
        service.listItems(GAME_ID);
        when(client.buy(GAME_ID, "wingpotmax")).thenReturn(new PurchaseResponse(true, 0, 3, 4, 10));

        assertThat(service.buy(GAME_ID, "wingpotmax").game().gold()).isZero();
    }

    /** The shop can still refuse on a 200, and the turn is gone either way. */
    @Test
    void reportsAnUpstreamRefusalAsALostTurnRatherThanAnError() {
        gameWith(120);
        service.listItems(GAME_ID);
        when(client.buy(GAME_ID, "cs")).thenReturn(new PurchaseResponse(false, 120, 3, 2, 10));

        PurchaseOutcome outcome = service.buy(GAME_ID, "cs");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.game().turn()).isEqualTo(10);
        assertThat(outcome.game().gold()).isEqualTo(120);
    }

    @Test
    void refusesEverythingWithoutALiveSession() {
        assertThatThrownBy(() -> service.listItems(GAME_ID)).isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> service.buy(GAME_ID, "cs")).isInstanceOf(SessionExpiredException.class);
        verify(client, never()).listShopItems(anyString());
    }

    @Test
    void refusesEverythingOnceTheLastLifeIsGone() {
        sessions.register(new GameState(GAME_ID, 0, 400, 2, 400, 9));

        assertThatThrownBy(() -> service.listItems(GAME_ID)).isInstanceOf(GameNotRunningException.class);
        assertThatThrownBy(() -> service.buy(GAME_ID, "cs")).isInstanceOf(GameNotRunningException.class);
        verify(client, never()).buy(anyString(), anyString());
    }

    /**
     * Browsing spends no turn, but every response here carries the game state beside the
     * catalogue, and that state is read before an upstream round trip. Left outside the lock, a
     * browse that started before a purchase and landed after it would answer with the gold the
     * player had a turn ago. Asserted as mutual exclusion rather than as a surviving figure, for
     * the same reason the board's test is: a correct state could be luck.
     */
    @Test
    void browsingTakesTheSameLockAsATurn() throws Exception {
        gameWith(120);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        when(client.listShopItems(GAME_ID)).thenAnswer(invocation -> {
            mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            Thread.sleep(20);
            inFlight.decrementAndGet();
            return CATALOGUE;
        });
        when(client.buy(eq(GAME_ID), anyString())).thenAnswer(invocation -> {
            mostAtOnce.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            Thread.sleep(20);
            inFlight.decrementAndGet();
            return new PurchaseResponse(true, 70, 4, 2, 10);
        });

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<Object>> work = pool.invokeAll(List.<Callable<Object>>of(
                    () -> service.listItems(GAME_ID),
                    () -> service.buy(GAME_ID, "hpot"),
                    () -> service.listItems(GAME_ID),
                    () -> service.knownCatalogue(GAME_ID)));
            for (Future<Object> each : work) {
                each.get();
            }
        }

        assertThat(mostAtOnce.get()).isEqualTo(1);
    }
}
