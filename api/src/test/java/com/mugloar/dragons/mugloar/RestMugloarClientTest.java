package com.mugloar.dragons.mugloar;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.GameNotFoundException;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.mugloar.exception.InvalidActionException;
import com.mugloar.dragons.mugloar.exception.MugloarException;
import com.mugloar.dragons.mugloar.exception.MugloarProtocolException;
import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises the client against recorded upstream payloads.
 *
 * <p>The client under test is assembled by the production {@link MugloarClientConfiguration}, so the
 * status mapping and retry policy asserted here are the ones the application actually runs.
 */
@WireMockTest
class RestMugloarClientTest {

    private static final String GAME_ID = "IeLKvlDb";

    /**
     * Generous enough that JVM warm-up cannot trip it. Spring's read timeout on the JDK client is a
     * deadline for consuming the whole response, not an idle-read timeout, so a tight value here
     * makes every test a race — the timeout cases set their own instead.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private MugloarClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wiremock) {
        client = buildClient(wiremock.getHttpBaseUrl(), READ_TIMEOUT);
    }

    private static MugloarClient buildClient(String baseUrl, Duration readTimeout) {
        MugloarProperties properties = new MugloarProperties(
                baseUrl,
                "dragons-of-mugloar-client/test",
                Duration.ofSeconds(2),
                readTimeout,
                2,
                Duration.ofMillis(1),
                Duration.ofMillis(5),
                Duration.ZERO,
                2.0);
        MugloarClientConfiguration configuration = new MugloarClientConfiguration();
        return configuration.mugloarClient(
                configuration.mugloarRestClient(
                        properties, configuration.mugloarRequestFactory(properties)),
                configuration.mugloarRetryTemplate(properties));
    }

    @Nested
    @DisplayName("reads every recorded payload")
    class HappyPath {

        @Test
        void startsAGame() {
            stubFor(post(urlPathEqualTo("/game/start"))
                    .willReturn(okJson(MugloarFixtures.load("game-start.json"))));

            GameStartedResponse game = client.startGame();

            assertThat(game).isEqualTo(new GameStartedResponse(GAME_ID, 3, 0, 0, 0, 0));
        }

        @Test
        @DisplayName("encrypted ads come back untouched, for the domain layer to decode")
        void listsAdsIncludingEncryptedOnes() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages"))
                    .willReturn(okJson(MugloarFixtures.load("messages.json"))));

            List<AdResponse> ads = client.listAds(GAME_ID);

            assertThat(ads).containsExactly(
                    new AdResponse("LTyNBlYB", "Help Robin Webster to steal a shipment of gold",
                            15, 7, null, "Piece of cake"),
                    new AdResponse("c2hFRmNq", "SGVscCBTaGVsYSBUb3duc2VuZCB0byBmaW5kIGEgbG9zdCBjYXQ=",
                            42, 5, 1, "UXVpdGUgbGlrZWx5"),
                    new AdResponse("nsWuOGxk", "Uryc Nyna Pnfr gb qrsraq gur ivyyntr",
                            180, 2, 2, "Fhvpvqr zvffvba"));
        }

        @Test
        void solvesAnAd() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/solve/LTyNBlYB"))
                    .willReturn(okJson(MugloarFixtures.load("solve-success.json"))));

            SolveResponse result = client.solve(GAME_ID, "LTyNBlYB");

            assertThat(result).isEqualTo(
                    new SolveResponse(true, 3, 17, 17, 3, "You successfully solved the mission!"));
        }

        @Test
        void reportsAFailedSolveAsAnOrdinaryResult() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/solve/LTyNBlYB"))
                    .willReturn(okJson(MugloarFixtures.load("solve-failure.json"))));

            SolveResponse result = client.solve(GAME_ID, "LTyNBlYB");

            assertThat(result.success()).isFalse();
            assertThat(result.lives()).isEqualTo(2);
        }

        @Test
        void listsShopItems() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/shop"))
                    .willReturn(okJson(MugloarFixtures.load("shop.json"))));

            List<ShopItemResponse> items = client.listShopItems(GAME_ID);

            assertThat(items).containsExactly(
                    new ShopItemResponse("hpot", "Healing potion", 50),
                    new ShopItemResponse("cs", "Claw Sharpening", 100),
                    new ShopItemResponse("wingpotmax", "Potion of Awesome Wings", 300));
        }

        @Test
        void buysAnItem() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/shop/buy/hpot"))
                    .willReturn(okJson(MugloarFixtures.load("buy-success.json"))));

            PurchaseResponse purchase = client.buy(GAME_ID, "hpot");

            assertThat(purchase).isEqualTo(new PurchaseResponse(true, 13, 4, 0, 3));
        }

        @Test
        @DisplayName("a rejected purchase is a 200 and is returned, not thrown")
        void surfacesARejectedPurchaseAsAResult() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/shop/buy/hpot"))
                    .willReturn(okJson(MugloarFixtures.load("buy-rejected.json"))));

            PurchaseResponse purchase = client.buy(GAME_ID, "hpot");

            assertThat(purchase.shoppingSuccess()).isFalse();
            assertThat(purchase.turn()).isEqualTo(4);
        }

        @Test
        void readsReputation() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/investigate/reputation"))
                    .willReturn(okJson(MugloarFixtures.load("reputation.json"))));

            ReputationResponse reputation = client.investigateReputation(GAME_ID);

            assertThat(reputation).isEqualTo(new ReputationResponse(0, 0, 0));
        }

        @Test
        void sendsTheConfiguredUserAgent() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/shop"))
                    .willReturn(okJson(MugloarFixtures.load("shop.json"))));

            client.listShopItems(GAME_ID);

            verify(getRequestedFor(urlPathEqualTo("/" + GAME_ID + "/shop"))
                    .withHeader("User-Agent", equalTo("dragons-of-mugloar-client/test")));
        }
    }

    @Nested
    @DisplayName("maps upstream failures, including the HTML ones")
    class ErrorMapping {

        @Test
        void mapsAnHtml404ToGameNotFound() {
            stubFor(get(urlPathEqualTo("/nope/messages")).willReturn(aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "text/html")
                    .withBody(MugloarFixtures.load("not-found.html"))));

            assertThatExceptionOfType(GameNotFoundException.class)
                    .isThrownBy(() -> client.listAds("nope"))
                    .satisfies(e -> assertThat(e.status()).isEqualTo(404));
        }

        @Test
        void mapsAnHtml400ToInvalidAction() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/solve/alreadyDone")).willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "text/html")
                    .withBody(MugloarFixtures.load("bad-request.html"))));

            assertThatExceptionOfType(InvalidActionException.class)
                    .isThrownBy(() -> client.solve(GAME_ID, "alreadyDone"));
        }

        @Test
        @DisplayName("410 is the one JSON error path, and means the game ended")
        void mapsA410ToGameOver() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/solve/LTyNBlYB")).willReturn(aResponse()
                    .withStatus(410)
                    .withHeader("Content-Type", "application/json")
                    .withBody(MugloarFixtures.load("game-over.json"))));

            assertThatExceptionOfType(GameOverException.class)
                    .isThrownBy(() -> client.solve(GAME_ID, "LTyNBlYB"));
        }

        @Test
        @DisplayName("a Cloudflare 403 is neither retried nor mistaken for a game error")
        void mapsAnUnexpectedStatusToTheBaseType() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages")).willReturn(aResponse()
                    .withStatus(403)
                    .withBody("error code: 1010")));

            assertThatExceptionOfType(MugloarException.class)
                    .isThrownBy(() -> client.listAds(GAME_ID))
                    .satisfies(e -> assertThat(e.status()).isEqualTo(403))
                    .isNotInstanceOf(MugloarUnavailableException.class);

            verify(1, getRequestedFor(urlPathEqualTo("/" + GAME_ID + "/messages")));
        }

        @Test
        void mapsAnUnreadableBodyToAProtocolFailure() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages"))
                    .willReturn(okJson("[{\"adId\": \"truncated\"")));

            assertThatExceptionOfType(MugloarProtocolException.class)
                    .isThrownBy(() -> client.listAds(GAME_ID));
        }

        @Test
        @DisplayName("an unreadable body is deterministic, so it is not retried")
        void doesNotRetryAnUnreadableBody() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages"))
                    .willReturn(okJson("[{\"adId\": \"truncated\"")));

            assertThatExceptionOfType(MugloarProtocolException.class)
                    .isThrownBy(() -> client.listAds(GAME_ID));

            verify(1, getRequestedFor(urlPathEqualTo("/" + GAME_ID + "/messages")));
        }
    }

    @Nested
    @DisplayName("retries only what is safe to repeat")
    class Retrying {

        @Test
        void retriesA5xxAndSucceeds() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/shop"))
                    .inScenario("flaky")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(aResponse().withStatus(503))
                    .willSetStateTo("recovered"));
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/shop"))
                    .inScenario("flaky")
                    .whenScenarioStateIs("recovered")
                    .willReturn(okJson(MugloarFixtures.load("shop.json"))));

            List<ShopItemResponse> items = client.listShopItems(GAME_ID);

            assertThat(items).hasSize(3);
            verify(2, getRequestedFor(urlPathEqualTo("/" + GAME_ID + "/shop")));
        }

        @Test
        void givesUpAfterTheConfiguredNumberOfAttempts() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatExceptionOfType(MugloarUnavailableException.class)
                    .isThrownBy(() -> client.listAds(GAME_ID));

            // One initial attempt plus the two configured retries.
            verify(3, getRequestedFor(urlPathEqualTo("/" + GAME_ID + "/messages")));
        }

        @Test
        void treatsAReadTimeoutAsUnavailable(WireMockRuntimeInfo wiremock) {
            MugloarClient impatient = buildClient(wiremock.getHttpBaseUrl(), Duration.ofMillis(200));
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages"))
                    .willReturn(okJson(MugloarFixtures.load("messages.json")).withFixedDelay(1500)));

            assertThatExceptionOfType(MugloarUnavailableException.class)
                    .isThrownBy(() -> impatient.listAds(GAME_ID));
        }

        @Test
        @DisplayName("a body that breaks off mid-read is unavailable, not unreadable, so it retries")
        void treatsATruncatedBodyAsUnavailable(WireMockRuntimeInfo wiremock) {
            MugloarClient impatient = buildClient(wiremock.getHttpBaseUrl(), Duration.ofMillis(300));
            // Dribbles the body out slowly enough that the read deadline fires part-way through,
            // which closes the stream underneath the parser.
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages"))
                    .willReturn(okJson(MugloarFixtures.load("messages.json"))
                            .withChunkedDribbleDelay(20, 2000)));

            assertThatExceptionOfType(MugloarUnavailableException.class)
                    .isThrownBy(() -> impatient.listAds(GAME_ID));

            verify(3, getRequestedFor(urlPathEqualTo("/" + GAME_ID + "/messages")));
        }

        @Test
        void doesNotRetryA4xx() {
            stubFor(get(urlPathEqualTo("/" + GAME_ID + "/messages")).willReturn(aResponse()
                    .withStatus(404)
                    .withBody(MugloarFixtures.load("not-found.html"))));

            assertThatExceptionOfType(GameNotFoundException.class)
                    .isThrownBy(() -> client.listAds(GAME_ID));

            verify(1, getRequestedFor(urlPathEqualTo("/" + GAME_ID + "/messages")));
        }

        @Test
        @DisplayName("solve is never repeated - a timed-out attempt may already have cost a turn")
        void neverRetriesSolve() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/solve/LTyNBlYB"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatExceptionOfType(MugloarUnavailableException.class)
                    .isThrownBy(() -> client.solve(GAME_ID, "LTyNBlYB"));

            verify(1, postRequestedFor(urlPathEqualTo("/" + GAME_ID + "/solve/LTyNBlYB")));
        }

        @Test
        @DisplayName("buying is never repeated - a failed purchase costs a turn all the same")
        void neverRetriesBuy() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/shop/buy/hpot"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatExceptionOfType(MugloarUnavailableException.class)
                    .isThrownBy(() -> client.buy(GAME_ID, "hpot"));

            verify(1, postRequestedFor(urlPathEqualTo("/" + GAME_ID + "/shop/buy/hpot")));
        }

        @Test
        void neverRetriesReputation() {
            stubFor(post(urlPathEqualTo("/" + GAME_ID + "/investigate/reputation"))
                    .willReturn(aResponse().withStatus(500)));

            assertThatExceptionOfType(MugloarUnavailableException.class)
                    .isThrownBy(() -> client.investigateReputation(GAME_ID));

            verify(1, postRequestedFor(urlPathEqualTo("/" + GAME_ID + "/investigate/reputation")));
        }
    }
}
