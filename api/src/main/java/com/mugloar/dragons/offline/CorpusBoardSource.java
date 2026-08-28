package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Deals ads by sampling boards recorded from live play.
 *
 * <p>Rows are bucketed by level and turn, because the reward a board offers depends on both and on
 * nothing else we could measure. A request draws a row from its own bucket, so the label and the
 * reward arrive together as the game actually paired them, rather than from two distributions
 * fitted separately.
 *
 * <p><b>The corpus covers the path the solver walks, and only that.</b> Every row was recorded
 * while the bot was playing, and the bot levels hard and early, so there is nothing in it for a
 * dragon sitting at level 0 on turn 80 — a corner a human player reaches easily and the recording
 * never visited. Those requests fall through to {@link #fallback} rather than to the nearest
 * populated bucket, because "nearest" across level means handing a level-0 dragon a board drawn
 * for a level-40 one, which is a worse answer than an honest guess.
 *
 * <p>Encryption is not sampled: the recording carries the decoded text, which is what the world
 * needs, and the flag was never in the decision the rows came from. It stays on the measured rates
 * in {@link OfflineProperties.Board}.
 */
class CorpusBoardSource implements BoardSource {

    private static final int LEVEL_BAND = 4;
    private static final int LEVELS = 16;
    private static final int TURN_BAND = 20;
    private static final int TURNS = 21;

    private final BoardCorpus corpus;
    private final BoardSource fallback;
    private final OfflineProperties.Board settings;
    private final List<Probability> labels;
    /** One entry list per (level band, turn band), flattened; an empty one means no coverage. */
    private final int[][][] buckets;

    CorpusBoardSource(
            BoardCorpus corpus, BoardSource fallback, OfflineProperties.Board settings) {
        this.corpus = corpus;
        this.fallback = fallback;
        this.settings = settings;
        this.labels = corpus.labels().stream().map(Probability::fromLabel).toList();
        this.buckets = bucket(corpus);
    }

    /** As recorded, so the offline board is the width the real one was. */
    @Override
    public int boardSize() {
        return corpus.boardSize();
    }

    @Override
    public GeneratedAd nextAd(int level, int turn, RandomGenerator rng) {
        int[][] bucket = buckets[key(level, turn)];
        if (bucket.length == 0) {
            return fallback.nextAd(level, turn, rng);
        }

        int[] entry = bucket[rng.nextInt(bucket.length)];
        return new GeneratedAd(
                OfflineIds.random(rng),
                corpus.messages().get(rng.nextInt(corpus.messages().size())),
                entry[BoardCorpus.REWARD],
                labels.get(entry[BoardCorpus.LABEL]),
                drawCipher(level, rng));
    }

    private static int[][][] bucket(BoardCorpus corpus) {
        List<List<int[]>> collected = new ArrayList<>(LEVELS * TURNS);
        for (int i = 0; i < LEVELS * TURNS; i++) {
            collected.add(new ArrayList<>());
        }
        for (int[] entry : corpus.entries()) {
            collected.get(key(entry[BoardCorpus.LEVEL], entry[BoardCorpus.TURN])).add(entry);
        }

        int[][][] buckets = new int[LEVELS * TURNS][][];
        for (int i = 0; i < buckets.length; i++) {
            List<int[]> held = collected.get(i);
            buckets[i] = held.toArray(new int[held.size()][]);
        }
        return buckets;
    }

    private static int key(int level, int turn) {
        return band(level, LEVEL_BAND, LEVELS) * TURNS + band(turn, TURN_BAND, TURNS);
    }

    private static int band(int value, int width, int count) {
        return Math.min(Math.max(0, value) / width, count - 1);
    }

    /** No encrypted ad appeared in 150 level-0 board entries, so encryption waits for progression. */
    private AdCipher drawCipher(int level, RandomGenerator rng) {
        if (level <= 0) {
            return AdCipher.NONE;
        }
        double roll = rng.nextDouble();
        if (roll < settings.base64Rate()) {
            return AdCipher.BASE64;
        }
        if (roll < settings.base64Rate() + settings.rot13Rate()) {
            return AdCipher.ROT13;
        }
        return AdCipher.NONE;
    }
}
