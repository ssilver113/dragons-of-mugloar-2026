package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.ads.SuccessModel;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Generates a board from parameters rather than from recorded data.
 *
 * <p>Since the recording landed this is the <em>fallback</em>, reached only where
 * {@link CorpusBoardSource} has nothing — the corners of the level and turn space the solver's own
 * play never visited, which a human player wanders into easily by not levelling.
 *
 * <p>It is the invented half of the offline world, and it stays honest about that. The reward scale is anchored to
 * the level's safe ceiling and then inflated by the turn, which reproduces the one shape the
 * exploration did establish — a board that outgrows a dragon standing still is what turns
 * {@code Piece of cake} from 0.95 into 0.04 over fifty turns — without the accident that comes of
 * making the scale level-blind, where a dragon that levels twice leaves the board behind for good
 * and the game becomes unloseable. Everything else here, the label mix and the spread most of all,
 * is a guess.
 *
 * <p>The label weights are the exploration's own sample counts, which were drawn to cover the
 * labels rather than to measure how often each appears, so they describe that sampling at least as
 * much as they describe the game.
 */
class ParametricBoardSource implements BoardSource {

    private static final List<Probability> LABELS = List.of(
            Probability.SURE_THING,
            Probability.WALK_IN_THE_PARK,
            Probability.PIECE_OF_CAKE,
            Probability.QUITE_LIKELY,
            Probability.HMMM,
            Probability.GAMBLE,
            Probability.RISKY,
            Probability.RATHER_DETRIMENTAL,
            Probability.PLAYING_WITH_FIRE,
            Probability.SUICIDE_MISSION,
            Probability.IMPOSSIBLE);

    private static final int[] LABEL_WEIGHTS = {50, 99, 163, 59, 60, 43, 41, 15, 34, 128, 300};
    private static final int LABEL_WEIGHT_TOTAL = 992;

    private static final List<String> PETITIONERS = List.of(
            "Robin Webster", "Alva Thorne", "Bram Ashby", "Cass Wilder", "Deryn Vale",
            "Edda Fenn", "Fitz Marrow", "Greer Holloway", "Hale Quist", "Imre Dunn",
            "Jessa Crowe", "Kip Ardent", "Lian Mercer", "Mabel Rook", "Nolan Frost",
            "Odile Sands", "Piers Galt", "Quinn Harrow", "Rasa Thorpe", "Silas Bly");

    private static final List<String> ERRANDS = List.of(
            "recover a ledger from the flooded counting house",
            "escort a grain barge past the weir",
            "settle a debt with the harbour guild",
            "find out who has been salting the wells",
            "carry a sealed writ to the upper keep",
            "clear a nest of hollow-beetles from the granary",
            "buy back a pawned wedding ring",
            "walk the tax rolls down to the assizes",
            "talk the miller out of a duel",
            "retrieve a lost hound from the marsh road",
            "deliver a physician to the quarantined row",
            "guard a funeral procession through the lower city");

    private final OfflineProperties.Board settings;
    private final SuccessModel model;

    ParametricBoardSource(OfflineProperties.Board settings, SuccessModel model) {
        this.settings = settings;
        this.model = model;
    }

    @Override
    public int boardSize() {
        return settings.boardSize();
    }

    @Override
    public GeneratedAd nextAd(int level, int turn, RandomGenerator rng) {
        Probability probability = drawLabel(rng);
        return new GeneratedAd(
                OfflineIds.random(rng),
                "Help %s to %s".formatted(pick(PETITIONERS, rng), pick(ERRANDS, rng)),
                drawReward(level, turn, rng),
                probability,
                drawCipher(level, rng));
    }

    private Probability drawLabel(RandomGenerator rng) {
        int roll = rng.nextInt(LABEL_WEIGHT_TOTAL);
        for (int i = 0; i < LABEL_WEIGHTS.length; i++) {
            roll -= LABEL_WEIGHTS[i];
            if (roll < 0) {
                return LABELS.get(i);
            }
        }
        return LABELS.getLast();
    }

    /** Log-normal around a median that tracks the level and is inflated by the turn. */
    private int drawReward(int level, int turn, RandomGenerator rng) {
        double median = model.safeRewardCeiling(level)
                * settings.rewardCeilingFraction()
                * (1.0 + settings.rewardGrowthPerTurn() * turn);
        double draw = median * Math.exp(settings.rewardSpread() * rng.nextGaussian());
        return Math.max(1, (int) Math.round(draw));
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

    private static <T> T pick(List<T> from, RandomGenerator rng) {
        return from.get(rng.nextInt(from.size()));
    }
}
