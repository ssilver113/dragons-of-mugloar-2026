package com.mugloar.dragons.shop;

import com.mugloar.dragons.game.CatalogueEntry;
import com.mugloar.dragons.game.GameSession;
import com.mugloar.dragons.game.GameSessionRegistry;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Browsing and buying.
 *
 * <p>The guards here earn their keep in a way the board's do not: the upstream reports a refused
 * purchase as a 200 and charges a turn for it either way, so an unaffordable or unknown item
 * caught here is a turn the player keeps. Prices come from the session's ledger, refreshed on
 * every browse and fetched on demand if the player has not browsed — listing the shop is free,
 * verified against the live API.
 *
 * <p>Buying spends a turn, so the affordability check and the purchase it authorises happen inside
 * the session's turn lock. The gold a purchase is measured against cannot be spent underneath it.
 *
 * <p>Browsing spends no turn and still takes that lock, because every response here carries the
 * game state beside the catalogue. Read outside it, that state is a snapshot from before an upstream
 * round trip, so a turn landing during the browse would be answered with a game one turn stale.
 */
@Service
public class ShopService {

    private final MugloarClient client;
    private final GameSessionRegistry sessions;

    public ShopService(MugloarClient client, GameSessionRegistry sessions) {
        this.client = client;
        this.sessions = sessions;
    }

    public ShopCatalogue listItems(String gameId) {
        GameSession session = sessions.require(gameId);
        return session.exclusively(() -> {
            GameState state = session.requireRunning();
            return new ShopCatalogue(state, fetchItems(gameId, session));
        });
    }

    /**
     * The catalogue, fetched once per game and then rebuilt from the session's ledger.
     *
     * <p>For the bot, which asks every turn and would otherwise spend a third of its upstream
     * calls re-reading prices that do not move. A human browsing still gets {@link #listItems},
     * which always asks: that price stability is measured rather than promised, and a player
     * looking at the shop should see what is there, not what was there.
     */
    public ShopCatalogue knownCatalogue(String gameId) {
        GameSession session = sessions.require(gameId);
        return session.exclusively(() -> {
            GameState state = session.requireRunning();
            return new ShopCatalogue(
                    state,
                    session.catalogue()
                            .map(ShopService::rebuild)
                            .orElseGet(() -> fetchItems(gameId, session)));
        });
    }

    public PurchaseOutcome buy(String gameId, String itemId) {
        GameSession session = sessions.require(gameId);
        return session.takeTurn(() -> {
            GameState state = session.requireRunning();
            if (!session.knowsShop()) {
                fetchItems(gameId, session);
            }

            int cost = session.itemCost(itemId)
                    .orElseThrow(() -> new ItemNotAvailableException(itemId));
            if (cost > state.gold()) {
                throw new InsufficientGoldException(itemId, cost, state.gold());
            }

            PurchaseResponse bought = client.buy(gameId, itemId);
            GameState updated = state.afterPurchase(bought);
            session.setState(updated);

            return new PurchaseOutcome(updated, itemId, bought.shoppingSuccess());
        });
    }

    private List<ShopItem> fetchItems(String gameId, GameSession session) {
        List<ShopItem> items = client.listShopItems(gameId).stream()
                .map(item -> item(item.id(), item.name(), item.cost()))
                .toList();
        session.recordShop(items.stream()
                .map(item -> new CatalogueEntry(item.id(), item.name(), item.cost()))
                .toList());
        return items;
    }

    private static List<ShopItem> rebuild(List<CatalogueEntry> catalogue) {
        return catalogue.stream()
                .map(entry -> item(entry.id(), entry.name(), entry.cost()))
                .toList();
    }

    private static ShopItem item(String id, String name, int cost) {
        return new ShopItem(id, name, cost, ItemEffect.forCost(cost));
    }
}
