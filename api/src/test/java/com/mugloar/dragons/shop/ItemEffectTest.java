package com.mugloar.dragons.shop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ItemEffectTest {

    @ParameterizedTest
    @CsvSource({
            "50,  EXTRA_LIFE",
            "100, LEVEL_UP",
            "300, DOUBLE_LEVEL_UP"})
    void readsTheEffectOffThePriceAsMeasured(int cost, ItemEffect expected) {
        assertThat(ItemEffect.forCost(cost)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 75, 200, 500})
    void claimsNothingAboutAPriceWeHaveNeverSeen(int cost) {
        assertThat(ItemEffect.forCost(cost)).isEqualTo(ItemEffect.UNKNOWN);
        assertThat(ItemEffect.forCost(cost).lives()).isZero();
        assertThat(ItemEffect.forCost(cost).levels()).isZero();
    }

    /**
     * The two tiers trade gold against turns and neither dominates, so the solver has a real
     * choice to make rather than a rule to follow. Pinned here because the recon write-up first
     * recorded this ratio the wrong way round.
     */
    @Test
    void theCheapTierBuysMoreLevelPerGoldAndTheDearOneMorePerTurn() {
        double perGold100 = (double) ItemEffect.LEVEL_UP.levels() / 100;
        double perGold300 = (double) ItemEffect.DOUBLE_LEVEL_UP.levels() / 300;
        assertThat(perGold100 / perGold300).isEqualTo(1.5);

        double perTurn100 = ItemEffect.LEVEL_UP.levels();
        double perTurn300 = ItemEffect.DOUBLE_LEVEL_UP.levels();
        assertThat(perTurn300 / perTurn100).isEqualTo(2.0);
    }

    @Test
    void thePotionBuysLivesAndNeverLevels() {
        assertThat(ItemEffect.EXTRA_LIFE.lives()).isEqualTo(1);
        assertThat(ItemEffect.EXTRA_LIFE.levels()).isZero();
    }
}
