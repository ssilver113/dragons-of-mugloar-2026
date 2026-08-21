package com.mugloar.dragons.mugloar.exception;

/**
 * The upstream rejected the action with a {@code 400} and an HTML body: an unknown ad, one already
 * solved or expired, or an encrypted ad whose {@code adId} was sent still encoded.
 */
public class InvalidActionException extends MugloarException {

    public InvalidActionException(String message) {
        super(message, 400);
    }
}
