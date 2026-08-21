package com.mugloar.dragons.mugloar.dto;

/**
 * One entry of {@code GET /{gameId}/messages}, exactly as it arrives on the wire.
 *
 * <p>When {@code encrypted} is non-null, {@code adId}, {@code message} and {@code probability} are
 * all encoded — 1 is Base64, 2 is ROT13. Decoding is the domain layer's job; this record carries
 * the raw form so the client stays a transport adapter.
 */
public record AdResponse(
        String adId,
        String message,
        int reward,
        int expiresIn,
        Integer encrypted,
        String probability) {
}
