package com.mugloar.dragons.mugloar.dto;

/** One entry of {@code GET /{gameId}/shop}. */
public record ShopItemResponse(String id, String name, int cost) {
}
