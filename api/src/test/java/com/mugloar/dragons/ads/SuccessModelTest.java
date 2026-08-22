package com.mugloar.dragons.ads;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuccessModelTest {

    private final SuccessModel model = SuccessModel.MEASURED;

    @ParameterizedTest
    @CsvSource({"0,112", "1,120", "4,145", "12,209", "26,322"})
    void ceilingClimbsWithLevel(int level, int ceiling) {
        assertThat(model.safeRewardCeiling(level)).isEqualTo(ceiling);
    }

    @Test
    void treatsANegativeLevelAsLevelZero() {
        assertThat(model.safeRewardCeiling(-3)).isEqualTo(model.safeRewardCeiling(0));
    }

    /**
     * The finding the whole solver rests on: the same label and the same reward are a near-certain
     * loss at level 0 and a good bet at level 12.
     */
    @Test
    void sameLabelAndRewardIsHopelessAtLowLevelAndSafeAtHigh() {
        double atLevelZero = model.estimate(Probability.PIECE_OF_CAKE, 180, 0);
        double atLevelTwelve = model.estimate(Probability.PIECE_OF_CAKE, 180, 12);

        assertThat(atLevelZero).isLessThan(0.1);
        assertThat(atLevelTwelve).isGreaterThan(0.7);
    }

    /**
     * The hand-driven exploration's cells, kept as an independent check on a model now fitted
     * mostly to something else. Only cells with at least 25 attempts behind them are asserted; the
     * thinner ones move by more than the model could ever track.
     *
     * <p>The widest residual is 0.27, at level 0 and 125 gold — a 26-attempt cell sitting on the
     * steepest part of the curve, where shifting the midpoint by a few gold moves the estimate a
     * long way. Against that, the fitted data reproduces every calibration band within 0.04.
     */
    @ParameterizedTest
    @CsvSource({
            // level, reward, observed rate for the top-tier labels — see docs/api-findings.md
            " 0,  75, 0.93",
            " 0, 125, 0.50",
            " 0, 175, 0.00",
            " 4,  75, 0.94",
            "12, 175, 0.94",
    })
    void staysCloseToTheHandMeasuredRates(int level, int reward, double observed) {
        double estimate = model.estimate(Probability.PIECE_OF_CAKE, reward, level);

        assertThat(estimate).isCloseTo(observed, org.assertj.core.data.Offset.offset(0.30));
    }

    /**
     * The finding the refit exists for. An ad worth twice what the dragon handles comfortably is
     * not a long shot, it is a donation: the previous model gave that band 0.29 and it came in at
     * 0.08 over 265 attempts. Holds at every level, because the whole curve scales with the
     * ceiling.
     */
    @ParameterizedTest
    @CsvSource({"0", "4", "12", "18", "30"})
    void treatsAnAdFarBeyondTheCeilingAsADonation(int level) {
        int reward = 2 * model.safeRewardCeiling(level);

        assertThat(model.estimate(Probability.PIECE_OF_CAKE, reward, level)).isLessThan(0.05);
    }

    @Test
    void fallsAsRewardRisesAtAFixedLevel() {
        double low = model.estimate(Probability.SURE_THING, 40, 3);
        double middling = model.estimate(Probability.SURE_THING, 140, 3);
        double high = model.estimate(Probability.SURE_THING, 240, 3);

        assertThat(low).isGreaterThan(middling);
        assertThat(middling).isGreaterThan(high);
    }

    @Test
    void risesAsLevelRisesAtAFixedReward() {
        double atZero = model.estimate(Probability.SURE_THING, 150, 0);
        double atSix = model.estimate(Probability.SURE_THING, 150, 6);
        double atTwelve = model.estimate(Probability.SURE_THING, 150, 12);

        assertThat(atZero).isLessThan(atSix);
        assertThat(atSix).isLessThan(atTwelve);
    }

    @Test
    void neverExceedsTheLabelsOwnPrior() {
        assertThat(model.estimate(Probability.PIECE_OF_CAKE, 0, 20))
                .isLessThanOrEqualTo(Probability.PIECE_OF_CAKE.prior());
    }

    @ParameterizedTest
    @EnumSource(value = Probability.class, names = {"IMPOSSIBLE", "SUICIDE_MISSION", "UNRECOGNISED"})
    void scoresTheExcludedLabelsAtZeroWhateverTheRewardOrLevel(Probability probability) {
        assertThat(model.estimate(probability, 10, 20)).isZero();
        assertThat(model.estimate(probability, 500, 0)).isZero();
    }

    @Test
    void keepsEveryEstimateAProbability() {
        for (Probability probability : Probability.values()) {
            for (int level = 0; level <= 30; level += 3) {
                for (int reward = 0; reward <= 600; reward += 25) {
                    assertThat(model.estimate(probability, reward, level)).isBetween(0.0, 1.0);
                }
            }
        }
    }

    @Test
    void refusesNonsenseParameters() {
        assertThatThrownBy(() -> new SuccessModel(0, 12, 1.25, 0.18))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SuccessModel(100, 12, 1.25, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SuccessModel(100, 12, 0, 0.18))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A ceiling that shrank as the dragon grew would invert every estimate the solver makes. */
    @Test
    void refusesACeilingThatFallsWithLevel() {
        assertThatThrownBy(() -> new SuccessModel(100, -8, 1.25, 0.18))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Zero is a legitimate fit — it says difficulty never scaled with level — so it is allowed. */
    @Test
    void acceptsACeilingThatDoesNotMoveWithLevel() {
        SuccessModel flat = new SuccessModel(100, 0, 1.25, 0.18);

        assertThat(flat.safeRewardCeiling(20)).isEqualTo(flat.safeRewardCeiling(0));
    }

    @Test
    void canBeRefittedWithoutTouchingTheCallers() {
        SuccessModel generous = new SuccessModel(200, 20, 1.25, 0.18);

        assertThat(generous.estimate(Probability.PIECE_OF_CAKE, 180, 0))
                .isGreaterThan(model.estimate(Probability.PIECE_OF_CAKE, 180, 0));
    }
}
