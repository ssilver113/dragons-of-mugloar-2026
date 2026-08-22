package com.mugloar.dragons.solver;

/** What became of one option the strategy weighed. */
public enum Verdict {

    CHOSEN,

    /** Playable or affordable, but something else was worth more this turn. */
    OUTRANKED,

    /** Risk-adjusted score at or below zero: the expected reward does not cover the risk to a life. */
    NOT_WORTH_A_LIFE,

    /** {@code Impossible} or {@code Suicide mission} — excluded outright, at any reward. */
    NEVER_ATTEMPT,

    /** The cipher or the label could not be read, so the ad cannot be scored, let alone chosen. */
    UNREADABLE,

    /** Costs more than the purse holds. */
    UNAFFORDABLE,

    /** Affordable, but its effect is not one we want right now. */
    NOT_NEEDED
}
