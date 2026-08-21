package com.mugloar.dragons.mugloar.exception;

import org.jspecify.annotations.Nullable;

/**
 * Base type for every failure of a call to the Mugloar API.
 *
 * <p>Thrown directly only for upstream responses that do not fit any of the specific subtypes — a
 * Cloudflare 403, for instance. Nothing in this hierarchy is retried except
 * {@link MugloarUnavailableException}.
 */
public class MugloarException extends RuntimeException {

    private final @Nullable Integer status;

    public MugloarException(String message, @Nullable Integer status) {
        super(message);
        this.status = status;
    }

    public MugloarException(String message, @Nullable Integer status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** The upstream HTTP status, or {@code null} when the failure happened before a response. */
    public @Nullable Integer status() {
        return status;
    }
}
