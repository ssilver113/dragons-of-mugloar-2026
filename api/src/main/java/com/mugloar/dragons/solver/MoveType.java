package com.mugloar.dragons.solver;

/** The three things a turn can be spent on. */
public enum MoveType {

    SOLVE_AD,

    BUY_ITEM,

    /**
     * The one action that costs a turn and risks nothing. Used only as a pass, when no ad is worth
     * a life and there is not enough gold to buy anything: burning a turn to redraw the board beats
     * attempting a mission we expect to lose.
     */
    INVESTIGATE_REPUTATION
}
