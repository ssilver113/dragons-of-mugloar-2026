package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.AdCipher;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.exception.GameNotFoundException;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static com.mugloar.dragons.offline.OfflineFixtures.MODEL;
import static com.mugloar.dragons.offline.OfflineFixtures.PROPERTIES;
import static com.mugloar.dragons.offline.OfflineFixtures.fixedBoard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedMugloarClientTest {

    private static final BoardSource BOARD =
            fixedBoard(20, Probability.PIECE_OF_CAKE, AdCipher.NONE);

    private static MugloarClient client(long seed, OfflineProperties properties) {
        return new SimulatedMugloarClient(
                new Random(seed), new ParametricBoardSource(properties.board(), MODEL), MODEL, properties);
    }

    /** The opening board of a fresh game, which is what a seed has to reproduce. */
    private static List<AdResponse> openingBoard(long seed) {
        MugloarClient client = client(seed, PROPERTIES);
        return client.listAds(client.startGame().gameId());
    }

    @Test
    void aStartedGameIsImmediatelyPlayable() {
        MugloarClient client =
                new SimulatedMugloarClient(new Random(1), BOARD, MODEL, PROPERTIES);

        GameStartedResponse started = client.startGame();
        List<AdResponse> ads = client.listAds(started.gameId());

        assertThat(started.gameId()).matches("[A-Za-z0-9]{8}");
        assertThat(ads).hasSize(PROPERTIES.board().boardSize());
        assertThat(client.listShopItems(started.gameId())).hasSize(11);
    }

    @Test
    void anUnknownGameIsTheUpstreamsNotFound() {
        MugloarClient client =
                new SimulatedMugloarClient(new Random(1), BOARD, MODEL, PROPERTIES);

        assertThatThrownBy(() -> client.listAds("nosuchgame"))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    void theSameSeedDealsTheSameGame() {
        assertThat(openingBoard(42)).isEqualTo(openingBoard(42));
    }

    @Test
    void differentSeedsDealDifferentGames() {
        assertThat(openingBoard(42)).isNotEqualTo(openingBoard(43));
    }

    @Test
    void theOldestGameIsDroppedOnceTheCapIsReached() {
        OfflineProperties capped = new OfflineProperties(7, 3, 2, 1L, PROPERTIES.board());
        MugloarClient client = client(1, capped);

        String first = client.startGame().gameId();
        String second = client.startGame().gameId();
        String third = client.startGame().gameId();

        assertThatThrownBy(() -> client.listAds(first)).isInstanceOf(GameNotFoundException.class);
        assertThat(client.listAds(second)).isNotEmpty();
        assertThat(client.listAds(third)).isNotEmpty();
    }
}
