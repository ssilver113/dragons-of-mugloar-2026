package com.mugloar.dragons.mugloar.exception;

/**
 * Cloudflare turned the call away with a {@code 429} and error 1015.
 *
 * <p>Its own type rather than a bare {@link MugloarException} because it is neither unexpected nor
 * a fault: the caller went too fast, and the only useful response is to slow down. Deliberately not
 * a {@link MugloarUnavailableException}, which is the one type the client retries — retrying into a
 * rate limiter is how a pause becomes a ban.
 *
 * <p>Recon reported no rate limiting across roughly 3000 requests in an hour. The solver disproved
 * that: three upstream calls per turn with no pause between them trips it after a few hundred
 * turns.
 */
public class MugloarRateLimitedException extends MugloarException {

    public MugloarRateLimitedException(String message) {
        super(message, 429);
    }
}
