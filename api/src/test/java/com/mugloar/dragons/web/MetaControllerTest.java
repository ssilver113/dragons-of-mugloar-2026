package com.mugloar.dragons.web;

import com.mugloar.dragons.mugloar.MugloarMode;
import com.mugloar.dragons.solver.StrategyParameters;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetaControllerTest {

    @Nested
    @WebMvcTest(MetaController.class)
    @Import({SimulatedWorld.class, ShippedSolver.class, KnownBuild.class})
    class Offline {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void saysSo() throws Exception {
            mockMvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offline").value(true));
        }
    }

    @Nested
    @WebMvcTest(MetaController.class)
    @Import({RealWorld.class, ShippedSolver.class, KnownBuild.class})
    class Live {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void saysNothingIsSimulated() throws Exception {
            mockMvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offline").value(false));
        }

        @Test
        void pricesALifeAtWhatTheSolverPlaysWith() throws Exception {
            mockMvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lifeValueGold").value(300.0));
        }

        @Test
        void namesTheBuildThatAnswered() throws Exception {
            mockMvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value("0.9"))
                    .andExpect(jsonPath("$.builtAt").value("2026-09-10T09:15:00Z"));
        }
    }

    /**
     * A jar assembled without the build-info task still has to serve a game, so the stamp is
     * reported absent rather than refused.
     */
    @Nested
    @WebMvcTest(MetaController.class)
    @Import({RealWorld.class, ShippedSolver.class})
    class Unstamped {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void reportsNoBuild() throws Exception {
            mockMvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offline").value(false))
                    .andExpect(jsonPath("$.version").doesNotExist())
                    .andExpect(jsonPath("$.builtAt").doesNotExist());
        }
    }

    /**
     * A tuned solver is what a benchmark sweep leaves running, and the advisor's balanced posture
     * has to be that solver's rather than the shipped default. Only a figure no constant carries
     * can show the endpoint reads the bean.
     */
    @Nested
    @WebMvcTest(MetaController.class)
    @Import({RealWorld.class, TunedSolver.class})
    class Tuned {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void pricesALifeAtWhateverTheRunningSolverHolds() throws Exception {
            mockMvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lifeValueGold").value(555.0));
        }
    }

    @TestConfiguration
    static class SimulatedWorld {

        @Bean
        MugloarMode mode() {
            return MugloarMode.OFFLINE;
        }
    }

    @TestConfiguration
    static class RealWorld {

        @Bean
        MugloarMode mode() {
            return MugloarMode.LIVE;
        }
    }

    @TestConfiguration
    static class ShippedSolver {

        @Bean
        StrategyParameters strategyParameters() {
            return StrategyParameters.DEFAULT;
        }
    }

    @TestConfiguration
    static class TunedSolver {

        @Bean
        StrategyParameters strategyParameters() {
            return new StrategyParameters(555.0, 1, 2, 0.2, 600);
        }
    }

    @TestConfiguration
    static class KnownBuild {

        @Bean
        BuildProperties buildProperties() {
            Properties entries = new Properties();
            entries.setProperty("version", "0.9");
            entries.setProperty("time", "2026-09-10T09:15:00Z");
            return new BuildProperties(entries);
        }
    }
}
