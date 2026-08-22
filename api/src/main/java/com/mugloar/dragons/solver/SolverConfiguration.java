package com.mugloar.dragons.solver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SolverConfiguration {

    /** One bean so the benchmark can sweep the parameters without touching the strategy. */
    @Bean
    StrategyParameters strategyParameters() {
        return StrategyParameters.DEFAULT;
    }
}
