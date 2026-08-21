package com.mugloar.dragons.mugloar.exception;

/**
 * The upstream answered, but the body could not be read as the shape the endpoint promises.
 *
 * <p>Deliberately separate from {@link MugloarUnavailableException}: a body we cannot parse is
 * deterministic, so retrying it only wastes time.
 */
public class MugloarProtocolException extends MugloarException {

    public MugloarProtocolException(String message, Integer status, Throwable cause) {
        super(message, status, cause);
    }
}
