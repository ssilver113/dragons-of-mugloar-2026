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

    /**
     * The upstream HTTP status, or {@code null} when the failure happened before a response.
     *
     * <p>Public although nothing above reads it: the mapping to an {@code ErrorCode} is by type,
     * and the one caller is the client's own test, which lives a package above this one. What it
     * asserts is worth keeping — the base type is thrown for every status we do not name, so the
     * status is the only thing separating a Cloudflare 403 from anything else that lands here.
     */
    public @Nullable Integer status() {
        return status;
    }
}
