package com.mugloar.dragons.bench;

/**
 * @param note why an aborted game ended, null for every other outcome
 */
record GameResult(
        String gameId, int score, int turns, int level, GameOutcome outcome, String note) {

    static GameResult aborted(String gameId, int score, int turns, int level, String note) {
        return new GameResult(gameId, score, turns, level, GameOutcome.ABORTED, note);
    }

    boolean counted() {
        return outcome != GameOutcome.ABORTED;
    }
}
