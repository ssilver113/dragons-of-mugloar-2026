package com.mugloar.dragons.mugloar.exception;

import org.jspecify.annotations.Nullable;

/**
 * The upstream could not be reached or failed server-side: a connect or read timeout, a dropped
 * connection, or a 5xx.
 *
 * <p>The only retryable failure in this hierarchy. Once retries are exhausted it is rethrown as-is.
 */
public class MugloarUnavailableException extends MugloarException {

    public MugloarUnavailableException(String message, @Nullable Integer status) {
        super(message, status);
    }

    public MugloarUnavailableException(String message, @Nullable Integer status, Throwable cause) {
        super(message, status, cause);
    }
}
