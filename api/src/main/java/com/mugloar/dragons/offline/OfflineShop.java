package com.mugloar.dragons.offline;

import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import java.util.List;

/**
 * The shop the offline world offers, recorded from a live {@code GET /shop} — ids, names, costs and
 * the order they arrive in.
 *
 * <p>The costs are what matter: the 50-gold potion adds a life, every 100-gold item adds exactly one
 * level and every 300-gold item exactly two, which is why {@code ItemEffect} keys off the price and
 * not the name. {@code Copper Plating} and {@code Iron Plating} are the same purchase at different
 * prices, and {@code Gasoline} and {@code Claw Sharpening} are the same purchase as each other.
 */
final class OfflineShop {

    static final List<ShopItemResponse> STOCK = List.of(
            new ShopItemResponse("hpot", "Healing potion", 50),
            new ShopItemResponse("cs", "Claw Sharpening", 100),
            new ShopItemResponse("gas", "Gasoline", 100),
            new ShopItemResponse("wax", "Copper Plating", 100),
            new ShopItemResponse("tricks", "Book of Tricks", 100),
            new ShopItemResponse("wingpot", "Potion of Stronger Wings", 100),
            new ShopItemResponse("ch", "Claw Honing", 300),
            new ShopItemResponse("rf", "Rocket Fuel", 300),
            new ShopItemResponse("iron", "Iron Plating", 300),
            new ShopItemResponse("mtrix", "Book of Megatricks", 300),
            new ShopItemResponse("wingpotmax", "Potion of Awesome Wings", 300));

    private OfflineShop() {
    }

    static ShopItemResponse find(String itemId) {
        return STOCK.stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElse(null);
    }
}
