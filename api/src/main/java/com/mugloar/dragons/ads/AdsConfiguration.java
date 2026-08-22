package com.mugloar.dragons.ads;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdsConfiguration {

    /** One bean so a refitted model can be substituted without touching the enricher. */
    @Bean
    SuccessModel successModel() {
        return SuccessModel.MEASURED;
    }
}
