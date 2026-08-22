package com.mugloar.dragons.bench;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.function.LongSupplier;

/**
 * One gate in front of every upstream call, so a run playing several games at once still spends the
 * upstream's patience at a single measured rate.
 *
 * <p>Cloudflare limits on burst rather than on hourly volume and does not publish the threshold, so
 * the interval is a starting guess that corrects itself: every rate-limited call widens it, and it
 * never narrows again within a run. A run that opened too fast settles instead of failing.
 */
final class Pacer {

    private final long ceilingNanos;
    private final double slowdownFactor;
    private final LongSupplier nanoTime;

    private long intervalNanos;
    private long nextSlotNanos;
    private boolean opened;

    Pacer(Duration interval, Duration maxInterval, double slowdownFactor, LongSupplier nanoTime) {
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Pacer interval must be positive");
        }
        if (maxInterval.compareTo(interval) < 0) {
            throw new IllegalArgumentException("Pacer max interval must not be below its interval");
        }
        if (slowdownFactor < 1.0) {
            throw new IllegalArgumentException("Pacer slowdown factor must be at least 1");
        }
        this.intervalNanos = interval.toNanos();
        this.ceilingNanos = maxInterval.toNanos();
        this.slowdownFactor = slowdownFactor;
        this.nanoTime = nanoTime;
    }

    Pacer(Duration interval, Duration maxInterval, double slowdownFactor) {
        this(interval, maxInterval, slowdownFactor, System::nanoTime);
    }

    /** Claims the next slot and reports how long the caller has to wait for it. */
    synchronized long reserveNanos() {
        long now = nanoTime.getAsLong();
        if (!opened) {
            opened = true;
            nextSlotNanos = now + intervalNanos;
            return 0L;
        }
        long slot = Math.max(now, nextSlotNanos);
        nextSlotNanos = slot + intervalNanos;
        return slot - now;
    }

    void awaitTurn() {
        long wait = reserveNanos();
        if (wait <= 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofNanos(wait));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while pacing an upstream call");
        }
    }

    /** Widens the gap after a rate-limited call. Monotonic: a run only ever slows down. */
    synchronized Duration slowDown() {
        intervalNanos = Math.min(ceilingNanos, Math.round(intervalNanos * slowdownFactor));
        return Duration.ofNanos(intervalNanos);
    }

    synchronized Duration interval() {
        return Duration.ofNanos(intervalNanos);
    }
}
