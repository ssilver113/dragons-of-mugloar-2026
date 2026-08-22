package com.mugloar.dragons.solver;

import java.util.List;

/**
 * One turn's choice and the evidence behind it.
 *
 * <p>The strategy returns this instead of a bare move, which is what keeps the decision log honest:
 * the log renders this record, so it cannot describe reasoning the code did not do.
 *
 * @param ads   every ad on the board, ranked best first, each with the verdict it earned
 * @param items the purchases weighed: the cheapest potion and one item from each level tier
 */
public record Decision(Move move, Reason reason, List<AdOption> ads, List<ItemOption> items) {

    public Decision {
        ads = List.copyOf(ads);
        items = List.copyOf(items);
    }
}
