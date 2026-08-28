package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.mugloar.exception.InvalidActionException;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.mugloar.dragons.offline.OfflineFixtures.MODEL;
import static com.mugloar.dragons.offline.OfflineFixtures.PROPERTIES;
import static com.mugloar.dragons.offline.OfflineFixtures.fixedBoard;
import static com.mugloar.dragons.offline.OfflineFixtures.scripted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every case here is a behaviour {@code docs/api-findings.md} recorded from the live API. The
 * simulation is only worth having if it reproduces the awkward ones, so those are what is
 * asserted: the turn a failed purchase still costs, the ad that leaves the board when it is
 * failed, the encoded id that does not solve.
 */
class SimulatedWorldTest {

    /**
     * Below every estimate and above every estimate. The scripted generator is not bound by
     * {@code nextDouble}'s real range, and going outside it keeps these outcomes decided by the
     * test rather than by how close to zero the success model happens to sit.
     */
    private static final double ALWAYS = -1.0;
    private static final double NEVER = 1.0;

    private SimulatedWorld world(RandomGenerator rng, BoardSource boards) {
        return new SimulatedWorld("OFFL1NE1", rng, boards, MODEL, PROPERTIES);
    }

    private SimulatedWorld cheapSafeBoard(RandomGenerator rng) {
        return world(rng, fixedBoard(20, Probability.PIECE_OF_CAKE, AdCipher.NONE));
    }

    @Nested
    class Starting {

        @Test
        void dealsAFullBoardOfFreshAds() {
            List<AdResponse> ads = cheapSafeBoard(scripted(ALWAYS)).ads();

            assertThat(ads).hasSize(PROPERTIES.board().boardSize());
            assertThat(ads).allSatisfy(ad -> assertThat(ad.expiresIn()).isEqualTo(7));
        }

        @Test
        void beginsAtTheDocumentedStartingState() {
            var started = cheapSafeBoard(scripted(ALWAYS)).started();

            assertThat(started.lives()).isEqualTo(3);
            assertThat(started.gold()).isZero();
            assertThat(started.level()).isZero();
            assertThat(started.score()).isZero();
            assertThat(started.turn()).isZero();
        }
    }

    @Nested
    class Solving {

        @Test
        void successPaysRewardIntoBothGoldAndScore() {
            SolveResponse solved = cheapSafeBoard(scripted(ALWAYS)).solve("ad0");

            assertThat(solved.success()).isTrue();
            assertThat(solved.gold()).isEqualTo(20);
            assertThat(solved.score()).isEqualTo(20);
            assertThat(solved.lives()).isEqualTo(3);
            assertThat(solved.message()).isEqualTo("You successfully solved the mission!");
        }

        @Test
        void failureCostsALifeAndPaysNothing() {
            SolveResponse failed = cheapSafeBoard(scripted(NEVER)).solve("ad0");

            assertThat(failed.success()).isFalse();
            assertThat(failed.gold()).isZero();
            assertThat(failed.lives()).isEqualTo(2);
            assertThat(failed.message()).isEqualTo("You failed on the mission!");
        }

        @Test
        void agesEveryRemainingAdByOne() {
            SimulatedWorld world = cheapSafeBoard(scripted(ALWAYS));

            world.solve("ad0");

            assertThat(world.ads())
                    .filteredOn(ad -> !ad.adId().equals("ad0"))
                    .filteredOn(ad -> ad.expiresIn() < 7)
                    .hasSize(PROPERTIES.board().boardSize() - 1);
        }

        @Test
        void advancesTheTurnByExactlyOne() {
            assertThat(cheapSafeBoard(scripted(ALWAYS)).solve("ad0").turn()).isEqualTo(1);
        }

        @Test
        void aFailedAdLeavesTheBoardRatherThanBeingOfferedAgain() {
            SimulatedWorld world = cheapSafeBoard(scripted(NEVER));

            world.solve("ad0");

            assertThat(world.ads()).extracting(AdResponse::adId).doesNotContain("ad0");
        }

        @Test
        void solvingTheSameAdTwiceIsRejected() {
            SimulatedWorld world = cheapSafeBoard(scripted(ALWAYS));
            world.solve("ad0");

            assertThatThrownBy(() -> world.solve("ad0"))
                    .isInstanceOf(InvalidActionException.class);
        }

        @Test
        void anUnknownAdIsRejected() {
            assertThatThrownBy(() -> cheapSafeBoard(scripted(ALWAYS)).solve("nosuchad"))
                    .isInstanceOf(InvalidActionException.class);
        }

        @Test
        void theBoardTopsBackUpAfterAnAdLeavesIt() {
            SimulatedWorld world = cheapSafeBoard(scripted(ALWAYS));

            world.solve("ad0");

            assertThat(world.ads()).hasSize(PROPERTIES.board().boardSize());
        }
    }

    @Nested
    class Encryption {

        private SimulatedWorld encryptedBoard(AdCipher cipher) {
            return world(scripted(ALWAYS), fixedBoard(20, Probability.PIECE_OF_CAKE, cipher));
        }

        @Test
        void theEncodedIdDoesNotSolve() {
            SimulatedWorld world = encryptedBoard(AdCipher.BASE64);
            String onTheWire = world.ads().getFirst().adId();

            assertThatThrownBy(() -> world.solve(onTheWire))
                    .isInstanceOf(InvalidActionException.class);
        }

        @Test
        void theDecodedIdDoes() {
            SimulatedWorld world = encryptedBoard(AdCipher.BASE64);

            assertThat(world.ads().getFirst().encrypted()).isEqualTo(1);
            assertThat(world.solve("ad0").success()).isTrue();
        }

        @Test
        void messageAndProbabilityTravelEncodedAlongsideTheId() {
            AdResponse ad = encryptedBoard(AdCipher.ROT13).ads().getFirst();

            assertThat(ad.adId()).isEqualTo(AdCipher.ROT13.encode("ad0"));
            assertThat(ad.message()).isEqualTo(AdCipher.ROT13.encode("Help someone"));
            assertThat(ad.probability()).isEqualTo(AdCipher.ROT13.encode("Piece of cake"));
            assertThat(ad.reward()).isEqualTo(20);
            assertThat(ad.expiresIn()).isEqualTo(7);
        }
    }

    @Nested
    class Shopping {

        private SimulatedWorld richWorld() {
            SimulatedWorld world =
                    world(scripted(ALWAYS), fixedBoard(400, Probability.SURE_THING, AdCipher.NONE));
            world.solve("ad0");
            return world;
        }

        @Test
        void aPotionBuysALifeAndNoLevel() {
            PurchaseResponse bought = richWorld().buy("hpot");

            assertThat(bought.shoppingSuccess()).isTrue();
            assertThat(bought.lives()).isEqualTo(4);
            assertThat(bought.level()).isZero();
            assertThat(bought.gold()).isEqualTo(350);
        }

        @Test
        void theCheapTierBuysOneLevelAndTheDearTierTwo() {
            assertThat(richWorld().buy("cs").level()).isEqualTo(1);
            assertThat(richWorld().buy("wingpotmax").level()).isEqualTo(2);
        }

        @Test
        void aPurchaseCostsATurnAndAgesTheBoard() {
            SimulatedWorld world = richWorld();

            PurchaseResponse bought = world.buy("hpot");

            assertThat(bought.turn()).isEqualTo(2);
            assertThat(world.ads()).anySatisfy(ad -> assertThat(ad.expiresIn()).isEqualTo(5));
        }

        @Test
        void tooLittleGoldStillCostsATurn() {
            SimulatedWorld world = cheapSafeBoard(scripted(ALWAYS));

            PurchaseResponse refused = world.buy("wingpotmax");

            assertThat(refused.shoppingSuccess()).isFalse();
            assertThat(refused.turn()).isEqualTo(1);
            assertThat(refused.gold()).isZero();
        }

        @Test
        void anUnknownItemIsAFailedPurchaseRatherThanAnError() {
            PurchaseResponse refused = richWorld().buy("nosuchitem");

            assertThat(refused.shoppingSuccess()).isFalse();
            assertThat(refused.turn()).isEqualTo(2);
        }

        @Test
        void spendingLowersGoldButNeverScore() {
            SimulatedWorld world = richWorld();

            PurchaseResponse bought = world.buy("wingpotmax");

            assertThat(bought.gold()).isEqualTo(100);
            assertThat(world.solve("ad1").score()).isEqualTo(800);
        }
    }

    @Nested
    class Investigating {

        @Test
        void costsExactlyOneTurnAndMovesNothingElse() {
            SimulatedWorld world = cheapSafeBoard(scripted(ALWAYS));

            var reputation = world.investigate();

            assertThat(reputation.people()).isZero();
            assertThat(world.ads()).anySatisfy(ad -> assertThat(ad.expiresIn()).isEqualTo(6));
            assertThat(world.solve("ad1").turn()).isEqualTo(2);
        }
    }

    @Nested
    class GameOver {

        private SimulatedWorld deadWorld() {
            SimulatedWorld world = cheapSafeBoard(scripted(NEVER));
            world.solve("ad0");
            world.solve("ad1");
            SolveResponse last = world.solve("ad2");

            assertThat(last.lives()).isZero();
            assertThat(last.message()).isEqualTo("You were defeated on your last mission!");
            return world;
        }

        @Test
        void everyLaterCallIsRefused() {
            SimulatedWorld world = deadWorld();

            assertThatThrownBy(world::ads).isInstanceOf(GameOverException.class);
            assertThatThrownBy(() -> world.solve("ad3")).isInstanceOf(GameOverException.class);
            assertThatThrownBy(() -> world.buy("hpot")).isInstanceOf(GameOverException.class);
            assertThatThrownBy(world::shopItems).isInstanceOf(GameOverException.class);
            assertThatThrownBy(world::investigate).isInstanceOf(GameOverException.class);
        }
    }
}
