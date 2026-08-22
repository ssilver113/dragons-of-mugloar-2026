package com.mugloar.dragons.bench;

/** How a benchmarked game finished. Only {@link #ABORTED} is left out of the distribution. */
enum GameOutcome {

    /** Lives ran out. The ordinary end, and the one the score distribution is about. */
    DIED,

    /** Hit the turn cap still alive. The score stands; the game was cut short. */
    TURN_CAP,

    /** Passed too many turns in a row with nothing affordable and no ad worth a life. */
    STALLED,

    /** An upstream failure ended it early, so the score says nothing about the strategy. */
    ABORTED
}
