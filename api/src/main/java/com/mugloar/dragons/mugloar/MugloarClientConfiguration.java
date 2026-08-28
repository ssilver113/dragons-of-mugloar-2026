package com.mugloar.dragons.mugloar;

import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The live client, and everything it needs to reach the network.
 *
 * <p>Conditional so that {@code mugloar.mode=offline} replaces the whole stack — request factory,
 * retry policy and all — rather than leaving an unused HTTP client configured beside a simulated
 * one. Absent the property this is what runs, so the real game stays the default.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mugloar.mode", havingValue = "live", matchIfMissing = true)
@EnableConfigurationProperties(MugloarProperties.class)
public class MugloarClientConfiguration {

    /**
     * The JDK HTTP client, chosen explicitly rather than left to auto-detection — recon confirmed
     * its signature passes Cloudflare, and pinning it means adding an HTTP library later cannot
     * silently change what the upstream sees.
     */
    @Bean
    ClientHttpRequestFactory mugloarRequestFactory(MugloarProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        return factory;
    }

    @Bean
    RestClient mugloarRestClient(MugloarProperties properties, ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) ->
                        RestMugloarClient.handleErrorStatus(
                                response.getStatusCode(),
                                RestMugloarClient.readBodySafely(response.getBody())))
                .build();
    }

    /**
     * Retries only {@link MugloarUnavailableException} — 5xx responses and transport failures.
     * A 4xx is a decision the upstream will repeat, and an unreadable body is deterministic, so
     * neither is worth a second attempt.
     */
    @Bean
    RetryTemplate mugloarRetryTemplate(MugloarProperties properties) {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(properties.maxRetries())
                .delay(properties.retryDelay())
                .multiplier(properties.retryMultiplier())
                .maxDelay(properties.retryMaxDelay())
                .jitter(properties.retryJitter())
                .includes(MugloarUnavailableException.class)
                .build();
        return new RetryTemplate(policy);
    }

    @Bean
    MugloarClient mugloarClient(RestClient mugloarRestClient, RetryTemplate mugloarRetryTemplate) {
        return new RestMugloarClient(mugloarRestClient, mugloarRetryTemplate);
    }

    @Bean
    MugloarMode mugloarMode() {
        return MugloarMode.LIVE;
    }
}
