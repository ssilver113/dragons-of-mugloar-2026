package com.mugloar.dragons.offline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for the offline world, active only under {@code mugloar.mode=offline}.
 *
 * @param adLifetime    turns a fresh ad survives; the API starts every one at 7
 * @param startingLives lives a new game begins with
 * @param maxGames      simulated games kept in memory before the least recently used is dropped;
 *                      an abandoned game is unreachable rather than expensive, so this only has to
 *                      stop a long-lived process from growing without bound
 * @param seed          fixes the whole run, so a reported game can be reproduced exactly; when
 *                      null, each process seeds itself
 * @param board         the generator's parameters, none of them measured
 */
@Validated
@ConfigurationProperties("mugloar.offline")
public record OfflineProperties(
        @Min(1) int adLifetime,
        @Min(1) int startingLives,
        @Min(1) int maxGames,
        Long seed,
        @NotNull @Valid Board board) {

    /**
     * Parameters for {@link ParametricBoardSource}, which deals only where the recorded corpus has
     * nothing to say. Every value here is a guess, which is why it is confined to that role.
     *
     * <p>The reward scale is anchored to the level's own safe ceiling because the alternative — a
     * scale that climbs only with the turn — produces a game nobody can lose: the dragon levels
     * past the board within twenty turns and never comes back to it. Anchoring keeps difficulty
     * roughly flat in level and rising in turn, which is the arc the real game has.
     *
     * <p>The cipher rates are the exception: those <em>are</em> measured, at 343 and 28 of 5130
     * observed board entries, and the corpus defers to them because the recording carries decoded
     * text and never carried the flag.
     *
     * @param boardSize             ads the board is topped up to each turn, when the corpus is
     *                              not the one saying so
     * @param rewardCeilingFraction median reward as a fraction of what this level can safely
     *                              handle, so the board scales with the dragon rather than being
     *                              left behind by it
     * @param rewardGrowthPerTurn   how fast that median climbs per turn on top of the level, which
     *                              is the pressure that eventually outruns any dragon
     * @param rewardSpread          log-normal spread around the median
     * @param base64Rate          share of ads encrypted as Base64, from 343/5130 observed
     * @param rot13Rate           share encrypted as ROT13, from 28/5130 observed
     */
    public record Board(
            @Min(1) int boardSize,
            @Positive double rewardCeilingFraction,
            @PositiveOrZero double rewardGrowthPerTurn,
            @Positive double rewardSpread,
            @PositiveOrZero double base64Rate,
            @PositiveOrZero double rot13Rate) {
    }
}
