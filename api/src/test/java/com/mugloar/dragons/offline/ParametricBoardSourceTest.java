package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.ads.SuccessModel;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static com.mugloar.dragons.offline.OfflineFixtures.BOARD_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * The generator is the invented half of the offline world, so what is asserted here is only what
 * the exploration actually established: that encryption waits for progression, that the board's
 * reward scale climbs with the turn, and that nothing leaves it that the domain cannot read.
 */
class ParametricBoardSourceTest {

    private static final int SAMPLE = 2000;

    private final ParametricBoardSource source =
            new ParametricBoardSource(BOARD_SETTINGS, SuccessModel.MEASURED);

    private List<GeneratedAd> sample(int level, int turn) {
        Random rng = new Random(7);
        return IntStream.range(0, SAMPLE).mapToObj(i -> source.nextAd(level, turn, rng)).toList();
    }

    @Test
    void noAdIsEncryptedAtLevelZero() {
        assertThat(sample(0, 0)).allSatisfy(ad -> assertThat(ad.cipher()).isEqualTo(AdCipher.NONE));
    }

    @Test
    void bothCiphersAppearOnceTheDragonHasLevelled() {
        assertThat(sample(4, 20)).extracting(GeneratedAd::cipher)
                .contains(AdCipher.NONE, AdCipher.BASE64, AdCipher.ROT13);
    }

    @Test
    void everyLabelIsOneTheDomainRecognises() {
        assertThat(sample(4, 20)).extracting(GeneratedAd::probability)
                .doesNotContain(Probability.UNRECOGNISED);
    }

    @Test
    void everyAdCarriesAPayingRewardAndAUsableId() {
        assertThat(sample(0, 0)).allSatisfy(ad -> {
            assertThat(ad.reward()).isPositive();
            assertThat(ad.adId()).matches("[A-Za-z0-9]{8}");
            assertThat(ad.message()).isNotBlank();
        });
    }

    @Test
    void theRewardScaleClimbsWithTheTurn() {
        assertThat(medianReward(sample(0, 50))).isGreaterThan(medianReward(sample(0, 0)));
    }

    @Test
    void theRewardScaleTracksTheLevel() {
        // Not because the real board was measured doing it, but because a level-blind board is one
        // a dragon leaves behind within twenty turns, and a game that cannot be lost is not one.
        assertThat(medianReward(sample(20, 0))).isGreaterThan(medianReward(sample(0, 0)));
    }

    @Test
    void aLevelledDragonIsNotHandedAnEasierBoard() {
        assertThat(feasibilityOf(sample(0, 0), 0))
                .isCloseTo(feasibilityOf(sample(20, 0), 20), withinPercentage(15));
    }

    private static double feasibilityOf(List<GeneratedAd> ads, int level) {
        return ads.stream()
                .mapToDouble(ad -> SuccessModel.MEASURED.feasibility(ad.reward(), level))
                .average()
                .orElseThrow();
    }

    private static int medianReward(List<GeneratedAd> ads) {
        List<Integer> sorted = ads.stream().map(GeneratedAd::reward).sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
