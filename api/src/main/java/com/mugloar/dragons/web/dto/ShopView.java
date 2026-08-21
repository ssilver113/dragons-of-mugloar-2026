package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.shop.ShopCatalogue;
import java.util.List;

/** Response of {@code GET /api/games/{gameId}/shop}. Order is the upstream's. */
public record ShopView(GameView game, List<ShopItemView> items) {

    public static ShopView from(ShopCatalogue catalogue) {
        return new ShopView(
                GameView.from(catalogue.game()),
                catalogue.items().stream().map(ShopItemView::from).toList());
    }
}
