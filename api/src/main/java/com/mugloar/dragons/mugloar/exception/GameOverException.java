package com.mugloar.dragons.mugloar.exception;

/**
 * The game has ended — any call after lives reach 0 returns {@code 410} with
 * {@code {"status":"Game Over"}}.
 *
 * <p>This is an expected end state rather than a fault, and the web layer renders it as one.
 */
public class GameOverException extends MugloarException {

    public GameOverException(String message) {
        super(message, 410);
    }
}
