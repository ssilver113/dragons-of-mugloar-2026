package com.mugloar.dragons.shop;

import com.mugloar.dragons.game.GameSession;
import com.mugloar.dragons.game.GameSessionRegistry;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Browsing and buying.
 *
 * <p>The guards here earn their keep in a way the board's do not: the upstream reports a refused
 * purchase as a 200 and charges a turn for it either way, so an unaffordable or unknown item
 * caught here is a turn the player keeps. Prices come from the session's ledger, refreshed on
 * every browse and fetched on demand if the player has not browsed — listing the shop is free,
 * verified against the live API.
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
        GameState state = session.requireRunning();
        return new ShopCatalogue(state, fetchItems(gameId, session));
    }

    public PurchaseOutcome buy(String gameId, String itemId) {
        GameSession session = sessions.require(gameId);
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
    }

    private List<ShopItem> fetchItems(String gameId, GameSession session) {
        List<ShopItem> items = client.listShopItems(gameId).stream()
                .map(item -> new ShopItem(
                        item.id(), item.name(), item.cost(), ItemEffect.forCost(item.cost())))
                .toList();
        session.recordShop(prices(items));
        return items;
    }

    private static Map<String, Integer> prices(List<ShopItem> items) {
        return items.stream()
                .collect(Collectors.toMap(ShopItem::id, ShopItem::cost, (first, second) -> first));
    }
}
