package com.mugloar.dragons.bench;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Plays a batch of games and reports what they scored.
 *
 * <p>This is the answer to "reaches 1000 points": a distribution rather than a screenshot of one
 * lucky run. It also writes the per-attempt corpus the success model is refitted from, which is why
 * the harness comes before the refit rather than after it — the model was fitted from a few hundred
 * hand-driven attempts, and the range where it goes wrong is one only a long run visits.
 *
 * <p>Runs under the {@code bench} profile with no web server. Games run a few at a time to hide the
 * round trip, but throughput belongs to the {@link Pacer}, which they share: adding concurrency
 * cannot make a run go faster than the upstream is willing to be asked.
 */
@Component
@Profile("bench")
class BenchmarkRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkRunner.class);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final BenchmarkGame game;
    private final BenchmarkProperties properties;
    private final Pacer pacer;
    private final PacedMugloarClient client;

    BenchmarkRunner(
            BenchmarkGame game,
            BenchmarkProperties properties,
            Pacer pacer,
            PacedMugloarClient client) {
        this.game = game;
        this.properties = properties;
        this.pacer = pacer;
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path file = Path.of(properties.outputDir()).resolve(
                "attempts-%s-%s.csv".formatted(
                        properties.label(), LocalDateTime.now().format(STAMP)));

        log.info("Playing {} games as '{}', {} at a time, {} between upstream calls",
                properties.games(), properties.label(),
                properties.concurrency(), pacer.interval());

        Instant started = Instant.now();
        try (AttemptLog attempts = AttemptLog.open(file)) {
            List<GameResult> results = playAll(attempts);
            log.info("\n{}", report(results, attempts, Duration.between(started, Instant.now())));
        }
    }

    private List<GameResult> playAll(AttemptLog attempts) throws InterruptedException {
        List<GameResult> results = new ArrayList<>(properties.games());
        ExecutorService pool = Executors.newFixedThreadPool(properties.concurrency());
        try {
            for (Future<GameResult> future : pool.invokeAll(tasks(attempts))) {
                try {
                    results.add(future.get());
                } catch (ExecutionException | CancellationException e) {
                    log.error("A benchmark game failed outright", e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return results;
    }

    /**
     * A game that ended on an upstream failure is followed by a pause. Whatever turned it away is
     * unlikely to have cleared by the time the next game would otherwise start.
     */
    private List<Callable<GameResult>> tasks(AttemptLog attempts) {
        AtomicInteger finished = new AtomicInteger();
        List<Callable<GameResult>> tasks = new ArrayList<>(properties.games());
        for (int i = 0; i < properties.games(); i++) {
            tasks.add(() -> {
                GameResult result = game.play(attempts);
                log.info("game {}/{} · {} · score {} · {} turns · level {}",
                        finished.incrementAndGet(), properties.games(),
                        result.outcome(), result.score(), result.turns(), result.level());
                if (result.outcome() == GameOutcome.ABORTED) {
                    Thread.sleep(properties.abortCooldown());
                }
                return result;
            });
        }
        return tasks;
    }

    String report(List<GameResult> results, AttemptLog attempts, Duration elapsed) {
        List<GameResult> counted = results.stream().filter(GameResult::counted).toList();
        ScoreDistribution scores = ScoreDistribution.of(
                counted.stream().map(GameResult::score).toList(), properties.scoreTarget());
        List<Integer> turns = counted.stream().map(GameResult::turns).sorted().toList();

        return """
                Benchmark '%s' — %d games, %d at a time
                  strategy   %s
                  model      %s

                  games      %d counted, %d aborted · %s
                  score      min %d · p25 %d · median %d · p75 %d · p95 %d · max %d · mean %.0f
                  under %d   %d of %d games (%.1f%%)
                  turns      median %s · max %s
                  upstream   %d calls in %s (%.1f/s), settled at %s between calls
                  attempts   %d rows in %s
                """.formatted(
                properties.label(), results.size(), properties.concurrency(),
                properties.strategy(), properties.model(),
                counted.size(), results.size() - counted.size(), outcomes(results),
                scores.min(), scores.p25(), scores.median(), scores.p75(), scores.p95(),
                scores.max(), scores.mean(),
                properties.scoreTarget(), scores.below(), scores.games(),
                scores.belowShare() * 100,
                turns.isEmpty() ? "—" : String.valueOf(median(turns)),
                turns.isEmpty() ? "—" : String.valueOf(turns.getLast()),
                client.calls(), human(elapsed), rate(client.calls(), elapsed), pacer.interval(),
                attempts.rows(), attempts.file());
    }

    private static int median(List<Integer> sorted) {
        return sorted.get(Math.max(0, (int) Math.ceil(sorted.size() / 2.0) - 1));
    }

    private static String outcomes(List<GameResult> results) {
        Map<GameOutcome, Integer> counts = new EnumMap<>(GameOutcome.class);
        results.forEach(result -> counts.merge(result.outcome(), 1, Integer::sum));
        return counts.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .reduce((one, other) -> one + " · " + other)
                .orElse("no games");
    }

    private static double rate(long calls, Duration elapsed) {
        double seconds = elapsed.toMillis() / 1000.0;
        return seconds <= 0 ? 0.0 : calls / seconds;
    }

    private static String human(Duration elapsed) {
        return "%dm %02ds".formatted(elapsed.toMinutes(), elapsed.toSecondsPart());
    }
}
