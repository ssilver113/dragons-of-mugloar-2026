package com.mugloar.dragons.game;

/** The ad is not on the board we last fetched: already solved, expired, or never there. */
public class AdNotAvailableException extends RuntimeException {

    public AdNotAvailableException(String adId) {
        super("Ad " + adId + " is not on the current board");
    }
}
