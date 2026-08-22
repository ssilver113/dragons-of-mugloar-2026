package com.mugloar.dragons.game;

import com.mugloar.dragons.mugloar.dto.ReputationResponse;

/**
 * How the three factions regard this dragon. Standing moves as missions are solved and failed;
 * nothing has been shown to depend on it, so it is displayed and never acted upon.
 */
public record Reputation(double people, double state, double underworld) {

    public static Reputation from(ReputationResponse response) {
        return new Reputation(response.people(), response.state(), response.underworld());
    }
}
