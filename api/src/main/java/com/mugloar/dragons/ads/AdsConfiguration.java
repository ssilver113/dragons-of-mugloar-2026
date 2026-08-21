package com.mugloar.dragons.ads;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdsConfiguration {

    /** One bean so Phase 10 can substitute a refitted model without touching the enricher. */
    @Bean
    SuccessModel successModel() {
        return SuccessModel.MEASURED;
    }
}
