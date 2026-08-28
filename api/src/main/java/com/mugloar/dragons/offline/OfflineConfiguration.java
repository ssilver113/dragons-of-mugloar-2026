package com.mugloar.dragons.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.MugloarMode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Wires the offline world in place of the HTTP client.
 *
 * <p>The two are mutually exclusive on {@code mugloar.mode}, so exactly one {@link MugloarClient}
 * bean exists and nothing downstream has to ask which. Live is the default: an offline game has to
 * be asked for.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mugloar.mode", havingValue = "offline")
@EnableConfigurationProperties(OfflineProperties.class)
public class OfflineConfiguration {

    private static final String CORPUS = "offline/board-corpus.json";

    /**
     * The recorded corpus, with the parametric generator behind it for the corners of the
     * (level, turn) space the recording never reached. Both are wanted: the corpus is the truthful
     * one, and it can only speak for the path the solver walked.
     */
    @Bean
    BoardSource offlineBoardSource(OfflineProperties properties, SuccessModel successModel)
            throws IOException {
        BoardSource parametric = new ParametricBoardSource(properties.board(), successModel);
        try (InputStream corpus = new ClassPathResource(CORPUS).getInputStream()) {
            // Its own mapper rather than the application's: this reads one fixed resource with no
            // configuration of its own, and borrowing the web one would tie the board the offline
            // world deals to how the HTTP layer happens to be set up.
            return new CorpusBoardSource(
                    new ObjectMapper().readValue(corpus, BoardCorpus.class),
                    parametric,
                    properties.board());
        }
    }

    @Bean
    MugloarClient mugloarClient(
            OfflineProperties properties, BoardSource boardSource, SuccessModel successModel) {
        RandomGenerator seeds =
                properties.seed() == null ? new Random() : new Random(properties.seed());
        return new SimulatedMugloarClient(seeds, boardSource, successModel, properties);
    }

    @Bean
    MugloarMode mugloarMode() {
        return MugloarMode.OFFLINE;
    }
}
