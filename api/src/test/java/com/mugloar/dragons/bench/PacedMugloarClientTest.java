package com.mugloar.dragons.bench;

import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.MugloarRateLimitedException;
import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PacedMugloarClientTest {

    private MugloarClient delegate;
    private Pacer pacer;
    private PacedMugloarClient client;

    @BeforeEach
    void setUp() {
        delegate = mock(MugloarClient.class);
        pacer = new Pacer(Duration.ofNanos(1), Duration.ofSeconds(2), 2.0);
        client = new PacedMugloarClient(delegate, pacer);
    }

    @Test
    void passesCallsStraightThrough() {
        GameStartedResponse started = new GameStartedResponse("IeLKvlDb", 3, 0, 0, 0, 0);
        when(delegate.startGame()).thenReturn(started);

        assertThat(client.startGame()).isSameAs(started);
    }

    @Test
    void countsEveryUpstreamCall() {
        when(delegate.solve("g", "a")).thenReturn(
                new SolveResponse(true, 3, 20, 20, 1, "Success"));

        client.startGame();
        client.listAds("g");
        client.solve("g", "a");

        assertThat(client.calls()).isEqualTo(3);
    }

    @Test
    void slowsEverybodyDownWhenOneCallIsRateLimited() {
        when(delegate.listAds("g")).thenThrow(new MugloarRateLimitedException("1015"));

        assertThatThrownBy(() -> client.listAds("g"))
                .isInstanceOf(MugloarRateLimitedException.class);

        assertThat(pacer.interval()).isEqualTo(Duration.ofNanos(2));
    }

    /** Only the rate limiter means "go slower". Everything else is a fault, not a pace. */
    @Test
    void leavesThePaceAloneForOtherFailures() {
        when(delegate.listAds("g")).thenThrow(new MugloarUnavailableException("503", 503));

        assertThatThrownBy(() -> client.listAds("g"))
                .isInstanceOf(MugloarUnavailableException.class);

        assertThat(pacer.interval()).isEqualTo(Duration.ofNanos(1));
    }
}
