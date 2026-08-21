package com.mugloar.dragons.game;

/**
 * No session for this game id — it was never started here, or it aged out.
 *
 * <p>Not recoverable by re-adopting the game: the upstream never reports {@code level}, so a
 * resumed session would score every ad against a level we know may be wrong.
 */
public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(String gameId) {
        super("No live session for game " + gameId);
    }
}
