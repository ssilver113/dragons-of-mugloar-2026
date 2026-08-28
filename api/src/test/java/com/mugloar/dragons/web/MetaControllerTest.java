package com.mugloar.dragons.web;

import com.mugloar.dragons.mugloar.MugloarMode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetaControllerTest {

    @Nested
    @WebMvcTest(MetaController.class)
    @Import(SimulatedWorld.class)
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
    @Import(RealWorld.class)
    class Live {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void saysNothingIsSimulated() throws Exception {
            mockMvc.perform(get("/api/meta"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offline").value(false));
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
}
