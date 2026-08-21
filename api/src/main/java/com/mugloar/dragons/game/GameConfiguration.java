package com.mugloar.dragons.game;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GameProperties.class)
public class GameConfiguration {

    /** Injected rather than called statically, so session expiry is testable without sleeping. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
