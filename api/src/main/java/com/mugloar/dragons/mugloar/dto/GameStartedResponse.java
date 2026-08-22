package com.mugloar.dragons.mugloar.dto;

/**
 * Response of {@code POST /game/start}.
 *
 * <p>The upstream also returns {@code highScore}, which is always 0 in this API version and is
 * deliberately not mapped.
 */
public record GameStartedResponse(
        String gameId,
        int lives,
        int gold,
        int level,
        int score,
        int turn) {
}
