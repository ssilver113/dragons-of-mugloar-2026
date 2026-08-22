package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.solver.Decision;
import com.mugloar.dragons.solver.MoveType;
import com.mugloar.dragons.solver.Reason;
import java.util.List;

/**
 * One turn's choice and everything that lost.
 *
 * <p>{@code reason} is a code, not a sentence: the numbers behind it travel alongside it and the
 * wording belongs to the client, which is also where the log is rendered.
 *
 * @param targetId the decoded ad id or the item id; null for a pass, which has nothing to aim at
 * @param ads      the board, ranked as the solver ranked it
 * @param items    the purchases weighed — the cheapest potion and one item from each level tier
 */
public record DecisionView(
        MoveType move,
        String targetId,
        Reason reason,
        List<AdOptionView> ads,
        List<ItemOptionView> items) {

    public static DecisionView from(Decision decision) {
        return new DecisionView(
                decision.move().type(),
                decision.move().targetId(),
                decision.reason(),
                decision.ads().stream().map(AdOptionView::from).toList(),
                decision.items().stream().map(ItemOptionView::from).toList());
    }
}
