package com.mugloar.dragons.bench;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDistributionTest {

    @Test
    void reportsEveryPercentileAsAScoreSomeGameActuallyReached() {
        List<Integer> scores = IntStream.rangeClosed(1, 100).boxed().toList();

        ScoreDistribution distribution = ScoreDistribution.of(scores, 1000);

        assertThat(distribution.min()).isEqualTo(1);
        assertThat(distribution.p25()).isEqualTo(25);
        assertThat(distribution.median()).isEqualTo(50);
        assertThat(distribution.p75()).isEqualTo(75);
        assertThat(distribution.p95()).isEqualTo(95);
        assertThat(distribution.max()).isEqualTo(100);
        assertThat(distribution.mean()).isEqualTo(50.5);
    }

    @Test
    void doesNotDependOnTheOrderTheGamesFinishedIn() {
        ScoreDistribution distribution =
                ScoreDistribution.of(List.of(3000, 800, 1500, 2200), 1000);

        assertThat(distribution.min()).isEqualTo(800);
        assertThat(distribution.max()).isEqualTo(3000);
        assertThat(distribution.median()).isEqualTo(1500);
    }

    /** The number that answers "reliably reaches 1000", so it counts games, not attempts. */
    @Test
    void countsTheGamesThatMissedTheTarget() {
        ScoreDistribution distribution =
                ScoreDistribution.of(List.of(400, 999, 1000, 1001, 5000), 1000);

        assertThat(distribution.below()).isEqualTo(2);
        assertThat(distribution.belowShare()).isEqualTo(0.4);
    }

    @Test
    void handlesASingleGame() {
        ScoreDistribution distribution = ScoreDistribution.of(List.of(1750), 1000);

        assertThat(distribution.games()).isEqualTo(1);
        assertThat(distribution.min()).isEqualTo(1750);
        assertThat(distribution.p95()).isEqualTo(1750);
        assertThat(distribution.max()).isEqualTo(1750);
        assertThat(distribution.below()).isZero();
    }

    /** A run where every game aborted still has to report, rather than divide by zero. */
    @Test
    void handlesNoGamesAtAll() {
        ScoreDistribution distribution = ScoreDistribution.of(List.of(), 1000);

        assertThat(distribution.games()).isZero();
        assertThat(distribution.belowShare()).isZero();
    }
}
