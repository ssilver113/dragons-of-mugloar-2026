package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.solver.ItemOption;
import com.mugloar.dragons.solver.Verdict;

/** One purchase the solver weighed, with the verdict it earned. */
public record ItemOptionView(
        String itemId,
        String name,
        int cost,
        int livesGained,
        int levelsGained,
        Verdict verdict) {

    public static ItemOptionView from(ItemOption option) {
        return new ItemOptionView(
                option.itemId(),
                option.name(),
                option.cost(),
                option.livesGained(),
                option.levelsGained(),
                option.verdict());
    }
}
