package com.mugloar.dragons.shop;

/** The purse is short, and the upstream would charge a turn for finding that out. */
public class InsufficientGoldException extends RuntimeException {

    public InsufficientGoldException(String itemId, int cost, int gold) {
        super("Item " + itemId + " costs " + cost + " but only " + gold + " gold is available");
    }
}
