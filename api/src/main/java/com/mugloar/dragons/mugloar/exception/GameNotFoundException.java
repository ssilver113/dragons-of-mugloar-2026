package com.mugloar.dragons.mugloar.exception;

/** The upstream does not know this {@code gameId} — a {@code 404}, with an HTML body. */
public class GameNotFoundException extends MugloarException {

    public GameNotFoundException(String message) {
        super(message, 404);
    }
}
