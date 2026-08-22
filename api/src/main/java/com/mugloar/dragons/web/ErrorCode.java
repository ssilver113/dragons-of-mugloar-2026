package com.mugloar.dragons.web;

/**
 * The machine-readable half of every error response. The UI branches on this rather than on the
 * status code, because several distinct situations share a status and the copy differs for each.
 */
public enum ErrorCode {

    /** A path variable did not look like an id. Nothing was sent upstream. */
    VALIDATION_FAILED,

    /** No live session for this game id — it aged out or the server restarted. Start a new game. */
    SESSION_EXPIRED,

    /** The game has ended. Terminal, not an error the player can retry out of. */
    GAME_OVER,

    /** The ad is gone from the board: solved already, or expired. Refetch the board. */
    AD_NOT_AVAILABLE,

    /** The shop does not stock this item, so buying it could only waste a turn. */
    ITEM_NOT_AVAILABLE,

    /** The item costs more than the purse holds. Refused before the turn was spent. */
    INSUFFICIENT_GOLD,

    /** The upstream does not know this game id. */
    GAME_NOT_FOUND,

    /** The upstream refused the action as invalid. */
    INVALID_ACTION,

    /** The upstream is rate limiting us. Not a fault — wait, then carry on. */
    UPSTREAM_RATE_LIMITED,

    /** The upstream is down or unreachable. Retrying later may work. */
    UPSTREAM_UNAVAILABLE,

    /** The upstream answered with something we could not read. */
    UPSTREAM_PROTOCOL,

    /** The upstream failed in a way we do not have a specific case for. */
    UPSTREAM_ERROR,

    /** Anything unhandled on our side. */
    INTERNAL_ERROR
}
