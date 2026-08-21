package com.mugloar.dragons.mugloar.dto;

/**
 * Response of {@code POST /{gameId}/solve/{adId}}.
 *
 * <p>Note the absence of {@code level} — the upstream never reports it here, so callers must track
 * it from purchases instead.
 */
public record SolveResponse(
        boolean success,
        int lives,
        int gold,
        int score,
        int turn,
        String message) {
}
