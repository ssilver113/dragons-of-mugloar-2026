package com.mugloar.dragons.solver;

/**
 * What the strategy decided to do. {@code targetId} is the decoded ad id or the item id, and is
 * null only for a pass, which has nothing to aim at.
 */
public record Move(MoveType type, String targetId) {

    public static Move solve(String adId) {
        return new Move(MoveType.SOLVE_AD, adId);
    }

    public static Move buy(String itemId) {
        return new Move(MoveType.BUY_ITEM, itemId);
    }

    public static Move investigateReputation() {
        return new Move(MoveType.INVESTIGATE_REPUTATION, null);
    }

    public boolean targets(String id) {
        return id.equals(targetId);
    }
}
