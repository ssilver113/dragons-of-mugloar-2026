package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.game.Reputation;

/**
 * Standing with the three factions, as of the turn that paid for it.
 *
 * <p>Rounded like every other estimate we publish — the upstream reports these to full double
 * precision, and no reading of the game depends on the fourth decimal.
 */
public record ReputationView(double people, double state, double underworld) {

    public static ReputationView from(Reputation reputation) {
        return new ReputationView(
                Rounding.estimate(reputation.people()),
                Rounding.estimate(reputation.state()),
                Rounding.estimate(reputation.underworld()));
    }
}
