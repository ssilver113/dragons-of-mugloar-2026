package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.ads.SuccessModel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

/** Rigged parts, so a test can decide what the board offers and how the dice fall. */
final class OfflineFixtures {

    static final OfflineProperties.Board BOARD_SETTINGS =
            new OfflineProperties.Board(10, 0.75, 0.02, 0.6, 0.067, 0.0055);

    static final OfflineProperties PROPERTIES =
            new OfflineProperties(7, 3, 200, 1L, BOARD_SETTINGS);

    static final SuccessModel MODEL = SuccessModel.MEASURED;

    private OfflineFixtures() {
    }

    /** Every ad identical but for a counted id, which keeps a board searchable by hand. */
    static BoardSource fixedBoard(int reward, Probability probability, AdCipher cipher) {
        AtomicInteger next = new AtomicInteger();
        return new BoardSource() {
            @Override
            public int boardSize() {
                return BOARD_SETTINGS.boardSize();
            }

            @Override
            public GeneratedAd nextAd(int level, int turn, RandomGenerator rng) {
                return new GeneratedAd(
                        "ad" + next.getAndIncrement(), "Help someone", reward, probability, cipher);
            }
        };
    }

    /**
     * A generator whose {@code nextDouble} is scripted, so a solve can be forced to succeed or fail
     * without leaning on the success model's arithmetic. Nothing else draws from it.
     */
    static RandomGenerator scripted(double... doubles) {
        AtomicInteger next = new AtomicInteger();
        return new RandomGenerator() {
            @Override
            public double nextDouble() {
                return doubles[Math.min(next.getAndIncrement(), doubles.length - 1)];
            }

            @Override
            public long nextLong() {
                throw new UnsupportedOperationException("scripted generator draws doubles only");
            }
        };
    }
}
