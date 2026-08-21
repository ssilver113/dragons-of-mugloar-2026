package com.mugloar.dragons.ads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProbabilityTest {

    @ParameterizedTest
    @CsvSource({
            "Sure thing,SURE_THING",
            "Walk in the park,WALK_IN_THE_PARK",
            "Piece of cake,PIECE_OF_CAKE",
            "Quite likely,QUITE_LIKELY",
            "Hmmm....,HMMM",
            "Gamble,GAMBLE",
            "Risky,RISKY",
            "Rather detrimental,RATHER_DETRIMENTAL",
            "Playing with fire,PLAYING_WITH_FIRE",
            "Suicide mission,SUICIDE_MISSION",
            "Impossible,IMPOSSIBLE",
    })
    void parsesEveryLabelTheApiUses(String label, Probability expected) {
        assertThat(Probability.fromLabel(label)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  Piece of cake  ", "PIECE OF CAKE", "piece of cake"})
    void toleratesCaseAndSurroundingSpace(String label) {
        assertThat(Probability.fromLabel(label)).isEqualTo(Probability.PIECE_OF_CAKE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Absolutely doable", "", "Hmmm", "Piece of cake!"})
    void mapsAnythingUnknownToUnrecognisedRatherThanThrowing(String label) {
        assertThat(Probability.fromLabel(label)).isEqualTo(Probability.UNRECOGNISED);
    }

    @Test
    void treatsANullLabelAsUnrecognised() {
        assertThat(Probability.fromLabel(null)).isEqualTo(Probability.UNRECOGNISED);
    }

    @Test
    void excludesOnlyTheTwoLabelsMeasuredAsHopeless() {
        assertThat(java.util.Arrays.stream(Probability.values()).filter(Probability::neverAttempt))
                .containsExactlyInAnyOrder(
                        Probability.SUICIDE_MISSION, Probability.IMPOSSIBLE, Probability.UNRECOGNISED);
    }

    @Test
    void ordersLabelsFromSafestToMostDangerous() {
        assertThat(Probability.SURE_THING.prior())
                .isGreaterThan(Probability.QUITE_LIKELY.prior())
                .isGreaterThan(Probability.GAMBLE.prior());
        assertThat(Probability.RATHER_DETRIMENTAL.prior())
                .isGreaterThan(Probability.IMPOSSIBLE.prior());
    }

    @Test
    void sharesOnePriorAcrossEachTier() {
        assertThat(Probability.SURE_THING.prior())
                .isEqualTo(Probability.WALK_IN_THE_PARK.prior())
                .isEqualTo(Probability.PIECE_OF_CAKE.prior());
    }

    @ParameterizedTest
    @EnumSource(value = Probability.class, mode = EnumSource.Mode.EXCLUDE, names = "UNRECOGNISED")
    void givesEveryRealLabelANonEmptyName(Probability probability) {
        assertThat(probability.label()).isNotBlank();
    }
}
