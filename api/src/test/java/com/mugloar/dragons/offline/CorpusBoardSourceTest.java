package com.mugloar.dragons.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static com.mugloar.dragons.offline.OfflineFixtures.BOARD_SETTINGS;
import static org.assertj.core.api.Assertions.assertThat;

class CorpusBoardSourceTest {

    private static final int SAMPLE = 500;

    /**
     * Two buckets and nothing else: level 0–3 at turns 0–19, and level 20–23 at turns 40–59. Every
     * other corner of the grid is uncovered, which is the case that has to reach the fallback.
     */
    private static final BoardCorpus CORPUS = new BoardCorpus(
            2,
            80,
            9,
            List.of("Piece of cake", "Impossible"),
            List.of("Help someone with something"),
            new int[][] {
                {0, 0, 40, 0}, {1, 5, 44, 0}, {2, 10, 48, 1},
                {20, 40, 300, 0}, {21, 50, 310, 1}, {22, 55, 320, 0},
            });

    private static final BoardSource NEVER_CALLED = new BoardSource() {
        @Override
        public int boardSize() {
            return 99;
        }

        @Override
        public GeneratedAd nextAd(int level, int turn, RandomGenerator rng) {
            return new GeneratedAd(
                    "fallback", "From the generator", 7, Probability.GAMBLE, AdCipher.NONE);
        }
    };

    private final CorpusBoardSource source =
            new CorpusBoardSource(CORPUS, NEVER_CALLED, BOARD_SETTINGS);

    private List<GeneratedAd> sample(int level, int turn) {
        Random rng = new Random(11);
        return IntStream.range(0, SAMPLE).mapToObj(i -> source.nextAd(level, turn, rng)).toList();
    }

    @Test
    void dealsTheRewardsRecordedForThisLevelAndTurn() {
        assertThat(sample(1, 8)).extracting(GeneratedAd::reward)
                .containsAnyOf(40, 44, 48)
                .doesNotContain(300, 310, 320);
    }

    @Test
    void aDifferentBucketDealsItsOwnRewards() {
        assertThat(sample(21, 45)).extracting(GeneratedAd::reward)
                .containsAnyOf(300, 310, 320)
                .doesNotContain(40, 44, 48);
    }

    @Test
    void labelsComeBackAsOnesTheDomainReads() {
        assertThat(sample(1, 8)).extracting(GeneratedAd::probability)
                .doesNotContain(Probability.UNRECOGNISED)
                .containsAnyOf(Probability.PIECE_OF_CAKE, Probability.IMPOSSIBLE);
    }

    @Test
    void aCornerTheRecordingNeverReachedFallsThroughToTheGenerator() {
        // The bot levels early, so nothing was ever recorded for a dragon still at level 0 on turn
        // 80. Answering that from the nearest bucket would hand it a level-20 board.
        assertThat(sample(0, 80)).allSatisfy(ad -> assertThat(ad.reward()).isEqualTo(7));
    }

    @Test
    void theBoardIsTheWidthThatWasRecorded() {
        assertThat(source.boardSize()).isEqualTo(9);
    }

    @Test
    void idsAreMintedRatherThanReplayed() {
        assertThat(sample(1, 8)).extracting(GeneratedAd::adId)
                .doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).matches("[A-Za-z0-9]{8}"));
    }

    @Test
    void theCommittedCorpusLoadsAndCoversTheOpeningBoard() throws IOException {
        try (InputStream in = new ClassPathResource("offline/board-corpus.json").getInputStream()) {
            BoardCorpus corpus = new ObjectMapper().readValue(in, BoardCorpus.class);

            assertThat(corpus.games()).isPositive();
            assertThat(corpus.boardSize()).isPositive();
            assertThat(corpus.messages()).isNotEmpty();
            assertThat(corpus.entries()).isNotEmpty();
            assertThat(corpus.labels()).allSatisfy(label ->
                    assertThat(Probability.fromLabel(label)).isNotEqualTo(Probability.UNRECOGNISED));

            // Turn 0 at level 0 is where every game starts, so it is the one bucket that must not
            // be empty — a fallback there would mean no player ever sees a recorded board.
            CorpusBoardSource live = new CorpusBoardSource(corpus, NEVER_CALLED, BOARD_SETTINGS);
            assertThat(live.nextAd(0, 0, new Random(3)).reward()).isNotEqualTo(7);
        }
    }
}
