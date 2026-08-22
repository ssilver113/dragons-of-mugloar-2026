package com.mugloar.dragons.bench;

import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.solver.StrategyParameters;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The harness is wired here rather than discovered an hour into a live run. Nothing in this test
 * reaches the network: the client being wrapped is a mock, and the context is never refreshed into
 * a running application, so the runner does not start playing.
 */
class BenchmarkConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(BenchmarkConfiguration.class)
            .withBean("mugloarClient", MugloarClient.class, () -> mock(MugloarClient.class))
            .withPropertyValues(
                    "bench.games=5",
                    "bench.concurrency=2",
                    "bench.request-interval=200ms",
                    "bench.max-request-interval=2s",
                    "bench.slowdown-factor=1.5",
                    "bench.max-turns=400",
                    "bench.max-consecutive-passes=40",
                    "bench.abort-cooldown=30s",
                    "bench.output-dir=build/bench",
                    "bench.label=test",
                    "bench.strategy.life-value-gold=300",
                    "bench.strategy.potion-threshold-lives=1",
                    "bench.strategy.target-level-base=2",
                    "bench.strategy.target-level-per-turn=0.2",
                    "bench.strategy.dear-tier-gold-floor=600",
                    "bench.model.ceiling-base=100",
                    "bench.model.ceiling-per-level=12",
                    "bench.model.midpoint-factor=1.25",
                    "bench.model.softness-factor=0.18",
                    "bench.score-target=1000");

    @Test
    void wrapsTheRealClientRatherThanReplacingIt() {
        contexts.withPropertyValues("spring.profiles.active=bench").run(context -> {
            assertThat(context).hasSingleBean(PacedMugloarClient.class);
            assertThat(context.getBean(MugloarClient.class))
                    .isInstanceOf(PacedMugloarClient.class);
            assertThat(context.getBeansOfType(MugloarClient.class)).hasSize(2);
        });
    }

    @Test
    void pacesAtTheConfiguredInterval() {
        contexts.withPropertyValues("spring.profiles.active=bench").run(context ->
                assertThat(context.getBean(Pacer.class).interval())
                        .isEqualTo(Duration.ofMillis(200)));
    }

    @Test
    void contributesNothingWhenTheProfileIsOff() {
        contexts.run(context -> {
            assertThat(context).doesNotHaveBean(Pacer.class);
            assertThat(context).doesNotHaveBean(PacedMugloarClient.class);
            assertThat(context).doesNotHaveBean(BenchmarkProperties.class);
        });
    }

    /** The sweep mechanism: one property, and the strategy plays with a different number. */
    @Test
    void takesTheStrategyAndTheModelFromConfiguration() {
        contexts.withPropertyValues(
                "spring.profiles.active=bench",
                "bench.strategy.target-level-per-turn=0.05",
                "bench.model.ceiling-per-level=9").run(context -> {
            assertThat(context.getBean(StrategyParameters.class).targetLevel(100))
                    .isEqualTo(7);
            assertThat(context.getBean(SuccessModel.class).safeRewardCeiling(10))
                    .isEqualTo(190);
        });
    }

    @Test
    void refusesToStartOnConfigurationThatWouldNotPaceAnything() {
        contexts.withPropertyValues("spring.profiles.active=bench", "bench.games=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
