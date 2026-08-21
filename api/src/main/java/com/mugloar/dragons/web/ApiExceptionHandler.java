package com.mugloar.dragons.web;

import com.mugloar.dragons.game.AdNotAvailableException;
import com.mugloar.dragons.game.GameNotRunningException;
import com.mugloar.dragons.game.SessionExpiredException;
import com.mugloar.dragons.mugloar.exception.GameNotFoundException;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.mugloar.exception.InvalidActionException;
import com.mugloar.dragons.mugloar.exception.MugloarException;
import com.mugloar.dragons.mugloar.exception.MugloarProtocolException;
import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import com.mugloar.dragons.shop.InsufficientGoldException;
import com.mugloar.dragons.shop.ItemNotAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every failure leaves here as an RFC 9457 {@code application/problem+json} body carrying a
 * {@link ErrorCode} in its {@code code} property.
 *
 * <p>The {@code detail} text is written here rather than taken from the exception message.
 * Upstream failures arrive as whole HTML error pages, and forwarding a snippet of one to the
 * browser would be both useless to a player and a small information leak. The real message is
 * logged instead.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SessionExpiredException.class)
    ProblemDetail handleSessionExpired(SessionExpiredException e) {
        log.debug("Session lookup failed: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, ErrorCode.SESSION_EXPIRED,
                "This game is no longer being tracked. Start a new one.");
    }

    /** Our own guard and the upstream's 410 mean the same thing to a player, so they share a code. */
    @ExceptionHandler({GameNotRunningException.class, GameOverException.class})
    ProblemDetail handleGameOver(RuntimeException e) {
        log.debug("Action on a finished game: {}", e.getMessage());
        return problem(HttpStatus.GONE, ErrorCode.GAME_OVER, "This game is over.");
    }

    @ExceptionHandler(AdNotAvailableException.class)
    ProblemDetail handleAdNotAvailable(AdNotAvailableException e) {
        log.debug("{}", e.getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.AD_NOT_AVAILABLE,
                "That ad is no longer on the board. Refresh to see the current one.");
    }

    @ExceptionHandler(ItemNotAvailableException.class)
    ProblemDetail handleItemNotAvailable(ItemNotAvailableException e) {
        log.debug("{}", e.getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.ITEM_NOT_AVAILABLE,
                "The shop does not sell that. Refresh to see what it stocks.");
    }

    /** Refused here rather than upstream, where finding out would have cost a turn. */
    @ExceptionHandler(InsufficientGoldException.class)
    ProblemDetail handleInsufficientGold(InsufficientGoldException e) {
        log.debug("{}", e.getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.INSUFFICIENT_GOLD,
                "You cannot afford that yet.");
    }

    @ExceptionHandler(GameNotFoundException.class)
    ProblemDetail handleGameNotFound(GameNotFoundException e) {
        log.warn("Upstream does not know the game: {}", e.getMessage());
        return problem(HttpStatus.NOT_FOUND, ErrorCode.GAME_NOT_FOUND,
                "The game service does not recognise this game.");
    }

    /**
     * 409 rather than 400: the request was well-formed, and what the upstream rejected was the
     * action's timing — typically an ad that went stale between the board and the click.
     */
    @ExceptionHandler(InvalidActionException.class)
    ProblemDetail handleInvalidAction(InvalidActionException e) {
        log.warn("Upstream rejected the action: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.INVALID_ACTION,
                "The game service rejected that action. Refresh and try again.");
    }

    @ExceptionHandler(MugloarUnavailableException.class)
    ProblemDetail handleUnavailable(MugloarUnavailableException e) {
        log.error("Mugloar unavailable", e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UPSTREAM_UNAVAILABLE,
                "The game service is not responding. Try again in a moment.");
    }

    @ExceptionHandler(MugloarProtocolException.class)
    ProblemDetail handleProtocol(MugloarProtocolException e) {
        log.error("Unreadable response from Mugloar", e);
        return problem(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_PROTOCOL,
                "The game service sent a response we could not read.");
    }

    @ExceptionHandler(MugloarException.class)
    ProblemDetail handleUpstream(MugloarException e) {
        log.error("Unexpected failure from Mugloar", e);
        return problem(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_ERROR,
                "The game service failed unexpectedly.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unhandled failure", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Something went wrong on our side.");
    }

    /** Path variables that failed their constraints; nothing reached the upstream. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        log.debug("Rejected malformed request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "That request contained an identifier we cannot accept."));
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code.name());
        return problem;
    }
}
