package com.mugloar.dragons.ads;

/** Warnings attached to an ad, for both the human player and the solver. */
public enum AdFlag {

    /** Last turn this ad exists: it is off the board after the next action, whatever that action is. */
    EXPIRING_NEXT_TURN,

    /**
     * Reward above what this level handles safely. The trap the recon run proved fatal — a
     * {@code Piece of cake} worth 180 gold is a certain loss at level 0.
     */
    OUT_OF_LEAGUE,

    /** {@code Impossible} or {@code Suicide mission}: measured at 0/300 and 7/128 (D23). */
    NEVER_ATTEMPT,

    /** The cipher or the probability label was not one we recognise, so the ad cannot be scored. */
    UNREADABLE
}
