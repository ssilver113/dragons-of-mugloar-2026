package com.mugloar.dragons.game;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param sessionTtl how long a game may sit idle before its session is evicted
 */
@Validated
@ConfigurationProperties("game")
public record GameProperties(@NotNull Duration sessionTtl) {
}
