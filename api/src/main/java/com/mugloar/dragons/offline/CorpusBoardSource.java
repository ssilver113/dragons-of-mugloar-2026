package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Deals ads by sampling boards recorded from live play.
 *
 * <p>Bucketed by level and turn, the only two things the reward was measured to depend on. Drawing
 * a whole row keeps the label and the reward paired as the game paired them.
 *
 * <p><b>The corpus covers the path the solver walks, and only that.</b> The bot levels hard and
 * early, so nothing was recorded for a dragon at level 0 on turn 80 — a corner a human reaches
 * easily. Those fall through to {@link #fallback} rather than to the nearest populated bucket,
 * since "nearest" across level hands a level-0 dragon a board drawn for a level-40 one.
 *
 * <p>Encryption is not sampled — the recording carries decoded text — so it stays on the measured
 * rates in {@link OfflineProperties.Board}.
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
