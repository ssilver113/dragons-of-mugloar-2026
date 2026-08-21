package com.mugloar.dragons.shop;

/** The shop does not sell this item, so buying it could only ever waste a turn. */
public class ItemNotAvailableException extends RuntimeException {

    public ItemNotAvailableException(String itemId) {
        super("Item " + itemId + " is not in this game's shop");
    }
}
