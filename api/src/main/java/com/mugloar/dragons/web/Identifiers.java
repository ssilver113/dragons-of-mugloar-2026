package com.mugloar.dragons.web;

/**
 * Ids are short opaque tokens upstream. Constraining them at the boundary keeps a path that could
 * never be one from reaching the network at all. The underscore and hyphen are allowed because a
 * decoded ad id is not guaranteed to stay strictly alphanumeric.
 */
final class Identifiers {

    static final String ID_PATTERN = "^[A-Za-z0-9_-]{1,64}$";

    private Identifiers() {
    }
}
