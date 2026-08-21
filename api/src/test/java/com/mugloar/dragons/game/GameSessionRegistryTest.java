package com.mugloar.dragons.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameSessionRegistryTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    private MovableClock clock;
    private GameSessionRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MovableClock(Instant.parse("2026-08-21T10:00:00Z"));
        registry = new GameSessionRegistry(new GameProperties(TTL), clock);
    }

    private GameSession register(String gameId) {
        return registry.register(new GameState(gameId, 3, 0, 0, 0, 0));
    }

    @Test
    void returnsTheSessionItRegistered() {
        GameSession registered = register("IeLKvlDb");

        assertThat(registry.require("IeLKvlDb")).isSameAs(registered);
    }

    @Test
    void refusesAGameIdItNeverSaw() {
        assertThatThrownBy(() -> registry.require("nosuchid"))
                .isInstanceOf(SessionExpiredException.class)
                .hasMessageContaining("nosuchid");
    }

    @Test
    void refusesAndDropsASessionThatSatIdlePastItsTtl() {
        register("IeLKvlDb");

        clock.advance(TTL.plusSeconds(1));

        assertThatThrownBy(() -> registry.require("IeLKvlDb"))
                .isInstanceOf(SessionExpiredException.class);
        assertThat(registry.size()).isZero();
    }

    @Test
    void keepsASessionAliveWhileItIsBeingUsed() {
        register("IeLKvlDb");

        for (int i = 0; i < 5; i++) {
            clock.advance(TTL.minusMinutes(1));
            assertThat(registry.require("IeLKvlDb")).isNotNull();
        }
    }

    @Test
    void sweepsIdleSessionsWhenANewGameStarts() {
        register("old-one");
        clock.advance(TTL.plusSeconds(1));

        register("new-one");

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void replacesTheSessionWhenTheSameGameIdIsRegisteredAgain() {
        register("IeLKvlDb");
        GameSession second = register("IeLKvlDb");

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.require("IeLKvlDb")).isSameAs(second);
    }

    /** A clock the test drives, so expiry is exercised without any sleeping. */
    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
