package com.mugloar.dragons.bench;

import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.solver.StrategyParameters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * How the headless run is paced and bounded, and what it plays with. Everything here is a run-time
 * choice rather than a measurement, so it lives in configuration and the README records what a
 * published run used.
 *
 * <p>{@code strategy} and {@code model} are the two records the design keeps tunable — the whole
 * reason they are records supplied as beans. Binding them here is what makes a sweep a command
 * line rather than a rebuild:
 *
 * <pre>
 *   ./gradlew bench -Pgames=16 -Pargs="--bench.label=slow --bench.strategy.target-level-per-turn=0.1"
 * </pre>
 *
 * @param games                how many games to play
 * @param concurrency          how many run at once. Throughput is set by {@code requestInterval},
 *                             not by this — concurrency only hides the round-trip latency
 * @param requestInterval      minimum gap between upstream calls, across all games
 * @param maxRequestInterval   how far that gap may widen after repeated rate limiting
 * @param slowdownFactor       what a rate-limited call multiplies the gap by
 * @param maxTurns             abandon a game that will not end. A safety net, not a strategy
 * @param maxConsecutivePasses how many turns in a row the solver may spend on reputation before the
 *                             game is called stalled. A pass never risks a life, so a broke dragon
 *                             on a hopeless board could otherwise pass forever
 * @param abortCooldown        how long to wait after a game ends on an upstream failure
 * @param outputDir            where the per-attempt corpus is written
 * @param label                names the run, and names its corpus file, so several configurations
 *                             can be compared without one overwriting another's evidence
 * @param scoreTarget          the score the assignment asks for, so the report can say how often it
 *                             was missed
 */
@Validated
@ConfigurationProperties("bench")
public record BenchmarkProperties(
        @Positive int games,
        @Positive int concurrency,
        @NotNull Duration requestInterval,
        @NotNull Duration maxRequestInterval,
        @PositiveOrZero double slowdownFactor,
        @Positive int maxTurns,
        @Positive int maxConsecutivePasses,
        @NotNull Duration abortCooldown,
        @NotBlank String outputDir,
        @NotBlank String label,
        @Positive int scoreTarget,
        @NotNull StrategyParameters strategy,
        @NotNull SuccessModel model) {
}
