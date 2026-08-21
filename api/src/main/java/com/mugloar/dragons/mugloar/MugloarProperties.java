package com.mugloar.dragons.mugloar;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings for talking to the Mugloar API. Validated at startup, so a bad value fails the
 * application rather than surfacing as a confusing runtime error on the first game.
 *
 * @param baseUrl        root of the upstream API, without a trailing slash
 * @param userAgent      sent explicitly: the API sits behind Cloudflare, which bans some client
 *                       signatures outright, and a descriptive UA turns a future rule change into
 *                       an obvious misconfiguration rather than a mystery outage (D27)
 * @param connectTimeout how long to wait for the connection to be established
 * @param readTimeout    how long to wait for the response once connected
 * @param maxRetries     retries *after* the initial attempt, and only for idempotent calls
 * @param retryDelay     backoff before the first retry
 * @param retryMaxDelay  ceiling the growing backoff is clamped to
 * @param retryJitter    random spread added to each backoff
 * @param retryMultiplier growth factor applied to the backoff after each attempt
 */
@Validated
@ConfigurationProperties("mugloar")
public record MugloarProperties(
        @NotBlank String baseUrl,
        @NotBlank String userAgent,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(0) long maxRetries,
        @NotNull Duration retryDelay,
        @NotNull Duration retryMaxDelay,
        @NotNull Duration retryJitter,
        @Positive double retryMultiplier) {
}
