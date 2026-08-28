package com.mugloar.dragons.offline;

import java.util.List;

/**
 * Boards recorded from live play, as the resource stores them.
 *
 * <p>One row per ad per turn, taken from the whole board rather than from the ads the solver chose
 * — the attempt corpus is filtered by the estimate under test and so cannot say what a board
 * offers. Rows are {@code [level, turn, reward, labelIndex]}, packed as integers because there are
 * thousands of them and every one is a small number.
 *
 * <p>Messages are pooled rather than kept with their row. Ad text carries no information the
 * simulation acts on, and the same few hundred strings recur across thousands of ads, so pairing
 * them back up would multiply the file for nothing.
 *
 * @param games      games the recording played
 * @param boards     boards seen across them
 * @param boardSize  ads per board, rounded from what was recorded
 * @param labels     probability labels, indexed by the fourth column of each entry
 * @param messages   the ad text pool
 * @param entries    the rows themselves
 */
record BoardCorpus(
        int games,
        int boards,
        int boardSize,
        List<String> labels,
        List<String> messages,
        int[][] entries) {

    static final int LEVEL = 0;
    static final int TURN = 1;
    static final int REWARD = 2;
    static final int LABEL = 3;
}
