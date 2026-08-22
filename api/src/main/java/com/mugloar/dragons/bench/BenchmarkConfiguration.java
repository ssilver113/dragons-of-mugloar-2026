package com.mugloar.dragons.bench;

import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.solver.StrategyParameters;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Everything the headless run needs, and nothing the application carries when it is not
 * benchmarking. The profile is what keeps a tuning harness out of the shipped service.
 */
@Configuration(proxyBeanMethods = false)
@Profile("bench")
@EnableConfigurationProperties(BenchmarkProperties.class)
class BenchmarkConfiguration {

    /**
     * The two tunables, taken from configuration rather than their compiled-in defaults. Sweeping
     * them is what the records were made records for; the defaults still apply everywhere else, so
     * the application a human runs is unaffected by whatever a benchmark was trying out.
     */
    @Bean
    @Primary
    StrategyParameters benchStrategyParameters(BenchmarkProperties properties) {
        return properties.strategy();
    }

    @Bean
    @Primary
    SuccessModel benchSuccessModel(BenchmarkProperties properties) {
        return properties.model();
    }

    @Bean
    Pacer pacer(BenchmarkProperties properties) {
        return new Pacer(
                properties.requestInterval(),
                properties.maxRequestInterval(),
                properties.slowdownFactor());
    }

    /**
     * Wraps the real client rather than replacing it, so the benchmark exercises the same
     * transport, the same retry policy and the same error mapping the application uses. The
     * qualifier names the bean being wrapped; without it this method would be asked to inject its
     * own result.
     */
    @Bean
    @Primary
    PacedMugloarClient pacedMugloarClient(
            @Qualifier("mugloarClient") MugloarClient delegate, Pacer pacer) {
        return new PacedMugloarClient(delegate, pacer);
    }
}
