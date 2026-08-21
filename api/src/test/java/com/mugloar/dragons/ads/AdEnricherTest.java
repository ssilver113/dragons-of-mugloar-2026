package com.mugloar.dragons.ads;

import com.mugloar.dragons.mugloar.dto.AdResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AdEnricherTest {

    private final AdEnricher enricher = new AdEnricher(SuccessModel.MEASURED);

    private static AdResponse ad(int reward, int expiresIn, String probability) {
        return new AdResponse("LTyNBlYB", "Help someone", reward, expiresIn, null, probability);
    }

    @Test
    void decodesBeforeScoringSoTheSolvableIdIsTheOneItReturns() {
        AdResponse encrypted = new AdResponse(
                "c2hFRmNq", "SGVscCBTaGVsYSBUb3duc2VuZCB0byBmaW5kIGEgbG9zdCBjYXQ=",
                42, 5, 1, "UXVpdGUgbGlrZWx5");

        EnrichedAd enriched = enricher.enrich(encrypted, 0);

        assertThat(enriched.adId()).isEqualTo("shEFcj");
        assertThat(enriched.message()).isEqualTo("Help Shela Townsend to find a lost cat");
        assertThat(enriched.probability()).isEqualTo(Probability.QUITE_LIKELY);
        assertThat(enriched.encrypted()).isTrue();
    }

    @Test
    void expectedValueIsRewardTimesTheLevelAwareEstimate() {
        EnrichedAd enriched = enricher.enrich(ad(80, 5, "Piece of cake"), 0);

        assertThat(enriched.expectedValue())
                .isCloseTo(80 * enriched.successProbability(), within(1e-9));
        assertThat(enriched.expectedValue()).isLessThan(80);
    }

    @Test
    void scoresTheSameAdDifferentlyForADifferentDragon() {
        AdResponse wire = ad(180, 5, "Piece of cake");

        assertThat(enricher.enrich(wire, 0).expectedValue())
                .isLessThan(enricher.enrich(wire, 12).expectedValue());
    }

    @ParameterizedTest
    @CsvSource({"0,true", "1,true", "2,false", "7,false"})
    void flagsAnAdOnItsLastTurn(int expiresIn, boolean expiring) {
        EnrichedAd enriched = enricher.enrich(ad(20, expiresIn, "Sure thing"), 0);

        assertThat(enriched.flags().contains(AdFlag.EXPIRING_NEXT_TURN)).isEqualTo(expiring);
    }

    @Test
    void flagsARewardAboveWhatThisLevelHandles() {
        assertThat(enricher.enrich(ad(180, 5, "Piece of cake"), 0).flags())
                .contains(AdFlag.OUT_OF_LEAGUE);
        assertThat(enricher.enrich(ad(180, 5, "Piece of cake"), 12).flags())
                .doesNotContain(AdFlag.OUT_OF_LEAGUE);
    }

    @Test
    void flagsTheTwoLabelsThatAreNeverWorthATurn() {
        assertThat(enricher.enrich(ad(500, 5, "Impossible"), 0).flags())
                .contains(AdFlag.NEVER_ATTEMPT);
        assertThat(enricher.enrich(ad(500, 5, "Suicide mission"), 0).flags())
                .contains(AdFlag.NEVER_ATTEMPT);
        assertThat(enricher.enrich(ad(500, 5, "Impossible"), 0).worthAttempting()).isFalse();
    }

    @Test
    void scoresAnUnknownLabelAtZeroAndFlagsItRatherThanGuessing() {
        EnrichedAd enriched = enricher.enrich(ad(90, 5, "Mildly inadvisable"), 3);

        assertThat(enriched.probability()).isEqualTo(Probability.UNRECOGNISED);
        assertThat(enriched.probabilityLabel()).isEqualTo("Mildly inadvisable");
        assertThat(enriched.successProbability()).isZero();
        assertThat(enriched.flags()).contains(AdFlag.UNREADABLE);
        assertThat(enriched.worthAttempting()).isFalse();
    }

    @Test
    void keepsAnUndecodableAdOnTheBoardButUnscored() {
        AdResponse wire = new AdResponse("abc", "def", 60, 1, 9, "ghi");

        EnrichedAd enriched = enricher.enrich(wire, 0);

        assertThat(enriched.flags()).contains(AdFlag.UNREADABLE, AdFlag.EXPIRING_NEXT_TURN);
        assertThat(enriched.successProbability()).isZero();
        assertThat(enriched.expectedValue()).isZero();
        assertThat(enriched.reward()).isEqualTo(60);
        assertThat(enriched.worthAttempting()).isFalse();
    }

    @Test
    void enrichesAWholeBoardInTheOrderItArrived() {
        List<EnrichedAd> board = enricher.enrich(
                List.of(ad(10, 5, "Impossible"), ad(90, 5, "Sure thing"), ad(30, 5, "Risky")), 0);

        assertThat(board).extracting(EnrichedAd::reward).containsExactly(10, 90, 30);
    }
}
