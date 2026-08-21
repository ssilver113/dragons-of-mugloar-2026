package com.mugloar.dragons.shop;

/** One item on offer, with the effect recon measured for its price band. */
public record ShopItem(String id, String name, int cost, ItemEffect effect) {
}
