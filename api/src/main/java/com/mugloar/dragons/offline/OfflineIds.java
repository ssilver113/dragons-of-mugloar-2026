package com.mugloar.dragons.offline;

import java.util.random.RandomGenerator;

/**
 * Game and ad ids, in the shape the upstream issues them: eight alphanumeric characters.
 *
 * <p>Shared because three things mint them and a collision between two of those would be a bug that
 * only showed up as an ad refusing to solve.
 */
final class OfflineIds {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 8;

    private OfflineIds() {
    }

    static String random(RandomGenerator rng) {
        StringBuilder id = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            id.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
