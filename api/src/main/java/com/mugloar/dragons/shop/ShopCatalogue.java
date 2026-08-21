package com.mugloar.dragons.shop;

import com.mugloar.dragons.game.GameState;
import java.util.List;

/** The shop as fetched, alongside the state that has to pay for it. */
public record ShopCatalogue(GameState game, List<ShopItem> items) {
}
