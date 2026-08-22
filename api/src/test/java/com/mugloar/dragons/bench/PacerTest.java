package com.mugloar.dragons.bench;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The clock is injected, so pacing is checked without spending the time it schedules. */
class PacerTest {

    private final AtomicLong now = new AtomicLong();

    @Test
    void letsTheFirstCallThroughImmediately() {
        assertThat(pacer(Duration.ofMillis(200)).reserveNanos()).isZero();
    }

    @Test
    void spacesConsecutiveCallsByTheInterval() {
        Pacer pacer = pacer(Duration.ofMillis(200));

        pacer.reserveNanos();

        assertThat(pacer.reserveNanos()).isEqualTo(Duration.ofMillis(200).toNanos());
        assertThat(pacer.reserveNanos()).isEqualTo(Duration.ofMillis(400).toNanos());
    }

    /**
     * The gap is measured between calls, not from each caller's arrival — a slow round trip that
     * already covered the interval must not be made to wait again.
     */
    @Test
    void doesNotWaitWhenTheIntervalHasAlreadyElapsed() {
        Pacer pacer = pacer(Duration.ofMillis(200));
        pacer.reserveNanos();

        advance(Duration.ofSeconds(5));

        assertThat(pacer.reserveNanos()).isZero();
    }

    @Test
    void widensTheGapWhenRateLimited() {
        Pacer pacer = pacer(Duration.ofMillis(200));

        assertThat(pacer.slowDown()).isEqualTo(Duration.ofMillis(300));
        assertThat(pacer.slowDown()).isEqualTo(Duration.ofMillis(450));
        assertThat(pacer.interval()).isEqualTo(Duration.ofMillis(450));
    }

    @Test
    void neverWidensPastItsCeiling() {
        Pacer pacer = new Pacer(
                Duration.ofMillis(200), Duration.ofMillis(500), 1.5, now::get);

        pacer.slowDown();
        pacer.slowDown();
        pacer.slowDown();

        assertThat(pacer.interval()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void appliesTheWidenedGapToTheNextCall() {
        Pacer pacer = pacer(Duration.ofMillis(200));
        pacer.reserveNanos();
        pacer.slowDown();

        assertThat(pacer.reserveNanos()).isEqualTo(Duration.ofMillis(200).toNanos());
        assertThat(pacer.reserveNanos()).isEqualTo(Duration.ofMillis(500).toNanos());
    }

    @Test
    void refusesConfigurationThatWouldNotPaceAnything() {
        assertThatThrownBy(() -> new Pacer(Duration.ZERO, Duration.ofSeconds(1), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pacer(Duration.ofSeconds(2), Duration.ofSeconds(1), 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Pacer(Duration.ofMillis(1), Duration.ofSeconds(1), 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Pacer pacer(Duration interval) {
        return new Pacer(interval, Duration.ofSeconds(2), 1.5, now::get);
    }

    private void advance(Duration by) {
        now.addAndGet(by.toNanos());
    }
}
