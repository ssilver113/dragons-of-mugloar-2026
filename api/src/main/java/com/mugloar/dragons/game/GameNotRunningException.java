package com.mugloar.dragons.game;

/** The game exists but has already ended, so the requested action can never succeed. */
public class GameNotRunningException extends RuntimeException {

    public GameNotRunningException(String gameId) {
        super("Game " + gameId + " is over");
    }
}
