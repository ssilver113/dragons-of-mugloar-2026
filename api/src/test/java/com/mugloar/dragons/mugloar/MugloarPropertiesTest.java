package com.mugloar.dragons.mugloar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * Bad configuration should stop the application at startup rather than surface as a puzzling
 * failure on the first game.
 */
class MugloarPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(MugloarClientConfiguration.class)
            .withPropertyValues(
                    "mugloar.base-url=https://dragonsofmugloar.com/api/v2",
                    "mugloar.user-agent=dragons-of-mugloar-client/1.0",
                    "mugloar.connect-timeout=3s",
                    "mugloar.read-timeout=10s",
                    "mugloar.max-retries=2",
                    "mugloar.retry-delay=250ms",
                    "mugloar.retry-max-delay=2s",
                    "mugloar.retry-jitter=100ms",
                    "mugloar.retry-multiplier=2.0");

    @Test
    void bindsValidConfigurationAndExposesTheClient() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MugloarClient.class);
            assertThat(context.getBean(MugloarProperties.class).baseUrl())
                    .isEqualTo("https://dragonsofmugloar.com/api/v2");
        });
    }

    @Test
    @DisplayName("a blank base URL fails startup, naming the offending field")
    void rejectsABlankBaseUrl() {
        contextRunner.withPropertyValues("mugloar.base-url=").run(context ->
                assertThat(context).getFailure()
                        .rootCause()
                        .hasMessageContaining("baseUrl"));
    }

    @Test
    @DisplayName("a negative retry count fails startup, naming the offending field")
    void rejectsNegativeRetries() {
        contextRunner.withPropertyValues("mugloar.max-retries=-1").run(context ->
                assertThat(context).getFailure()
                        .rootCause()
                        .hasMessageContaining("maxRetries"));
    }

    @Test
    @DisplayName("a zero backoff multiplier fails startup, naming the offending field")
    void rejectsANonPositiveMultiplier() {
        contextRunner.withPropertyValues("mugloar.retry-multiplier=0").run(context ->
                assertThat(context).getFailure()
                        .rootCause()
                        .hasMessageContaining("retryMultiplier"));
    }

    @Test
    @DisplayName("retries may be switched off entirely")
    void allowsZeroRetries() {
        contextRunner.withPropertyValues("mugloar.max-retries=0")
                .run(context -> assertThat(context.getBean(MugloarProperties.class).maxRetries())
                        .isZero());
    }

    @Test
    void bindsDurationsRatherThanRawNumbers() {
        contextRunner.run(context -> {
            MugloarProperties properties = context.getBean(MugloarProperties.class);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.retryDelay()).isEqualTo(Duration.ofMillis(250));
        });
    }
}
