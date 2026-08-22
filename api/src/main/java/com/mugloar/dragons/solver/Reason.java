package com.mugloar.dragons.solver;

/**
 * Why the turn went where it did. A code rather than a sentence: the numbers behind it travel with
 * the decision, and phrasing them is the frontend's job.
 */
public enum Reason {

    /** Lives are at or below the threshold and a potion is affordable. Survival outranks earning. */
    HEALING_LOW_ON_LIVES,

    /** The dragon is behind the level the board expects by now, and can afford to catch up. */
    LEVELLING_BEHIND_TARGET,

    /** The ordinary case: the ad with the best reward once the risk to a life is priced in. */
    BEST_RISK_ADJUSTED_AD,

    /** Nothing on the board was worth a life, so the turn bought a level instead. */
    LEVELLING_NO_AD_WORTH_A_LIFE,

    /** Nothing was worth a life and levelling was out of reach, so the turn bought a life. */
    HEALING_NO_AD_WORTH_A_LIFE,

    /** Nothing was worth a life and nothing was affordable. The turn passes rather than gambles. */
    PASSING_NOTHING_WORTH_A_TURN
}
