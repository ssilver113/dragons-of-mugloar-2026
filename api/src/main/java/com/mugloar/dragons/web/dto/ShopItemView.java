package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.shop.ShopItem;

/**
 * One item on offer. The effect travels as the two numbers it moves rather than as a name, so the
 * client can both describe the purchase and predict it optimistically without knowing our enum.
 * Both are zero for a price we have never measured.
 */
public record ShopItemView(String id, String name, int cost, int livesGained, int levelsGained) {

    public static ShopItemView from(ShopItem item) {
        return new ShopItemView(
                item.id(),
                item.name(),
                item.cost(),
                item.effect().lives(),
                item.effect().levels());
    }
}
