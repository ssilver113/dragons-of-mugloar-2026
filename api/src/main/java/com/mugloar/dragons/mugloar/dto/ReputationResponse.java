package com.mugloar.dragons.mugloar.dto;

/** Response of {@code POST /{gameId}/investigate/reputation}. The call consumes a turn. */
public record ReputationResponse(double people, double state, double underworld) {
}
