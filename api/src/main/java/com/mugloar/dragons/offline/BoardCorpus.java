package com.mugloar.dragons.offline;

import java.util.List;

/**
 * Boards recorded from live play, as the resource stores them.
 *
 * <p>One row per ad per turn, from the whole board rather than the ads the solver chose. Rows are
 * {@code [level, turn, reward, labelIndex]}, packed as integers because there are thousands of
 * small numbers. Messages are pooled: the simulation acts on none of the text, and the same few
 * hundred strings recur across thousands of ads.
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
