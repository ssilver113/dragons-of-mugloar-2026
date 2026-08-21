package com.mugloar.dragons.mugloar.dto;

/**
 * Response of {@code POST /{gameId}/shop/buy/{itemId}}.
 *
 * <p>A rejected purchase is reported here as {@code shoppingSuccess=false} on an HTTP 200, and it
 * still costs a turn — so this flag, not the status code, decides whether the buy happened.
 */
public record PurchaseResponse(
        boolean shoppingSuccess,
        int gold,
        int lives,
        int level,
        int turn) {
}
