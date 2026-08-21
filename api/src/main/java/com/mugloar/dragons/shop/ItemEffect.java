package com.mugloar.dragons.shop;

/**
 * What an item actually does, keyed off its price rather than its name.
 *
 * <p>Recon measured every item from a fresh game: the 50-gold potion adds a life, all five
 * 100-gold items add exactly one level, and all five 300-gold items add exactly two. The names are
 * pure flavour — {@code Claw Sharpening} and {@code Book of Tricks} are the same purchase. That
 * makes the 300-gold tier 1.5× the level per gold, which is the fact the solver will trade on.
 */
public enum ItemEffect {

    EXTRA_LIFE(1, 0),
    LEVEL_UP(0, 1),
    DOUBLE_LEVEL_UP(0, 2),

    /** A price we have never seen. Nothing is claimed about it rather than guessed. */
    UNKNOWN(0, 0);

    private final int lives;
    private final int levels;

    ItemEffect(int lives, int levels) {
        this.lives = lives;
        this.levels = levels;
    }

    public static ItemEffect forCost(int cost) {
        return switch (cost) {
            case 50 -> EXTRA_LIFE;
            case 100 -> LEVEL_UP;
            case 300 -> DOUBLE_LEVEL_UP;
            default -> UNKNOWN;
        };
    }

    public int lives() {
        return lives;
    }

    public int levels() {
        return levels;
    }
}
