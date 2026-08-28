package com.mugloar.dragons.offline;

import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.RestMugloarClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mode switch is the whole feature: get it wrong and an offline game quietly plays the live
 * one, or a live game plays a simulation. Both directions are asserted against a real context.
 */
class OfflineModeWiringTest {

    @Nested
    @SpringBootTest(properties = "mugloar.mode=offline")
    class Offline {

        @Autowired
        private MugloarClient client;

        @Test
        void theSimulatedClientReplacesTheHttpOne() {
            assertThat(client).isInstanceOf(SimulatedMugloarClient.class);
        }

        @Test
        void aGameCanBePlayedWithNoNetwork() {
            String gameId = client.startGame().gameId();

            assertThat(client.listAds(gameId)).isNotEmpty();
            assertThat(client.listShopItems(gameId)).isNotEmpty();
        }
    }

    @Nested
    @SpringBootTest
    class Live {

        @Autowired
        private MugloarClient client;

        @Test
        void isWhatRunsWhenNothingAsksForOffline() {
            assertThat(client).isInstanceOf(RestMugloarClient.class);
        }
    }
}
