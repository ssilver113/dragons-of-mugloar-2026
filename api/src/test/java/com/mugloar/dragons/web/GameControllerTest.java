package com.mugloar.dragons.web;

import com.mugloar.dragons.ads.AdFlag;
import com.mugloar.dragons.ads.EnrichedAd;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.game.AdBoard;
import com.mugloar.dragons.game.AdNotAvailableException;
import com.mugloar.dragons.game.GameNotRunningException;
import com.mugloar.dragons.game.GameService;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.game.SessionExpiredException;
import com.mugloar.dragons.game.SolveOutcome;
import com.mugloar.dragons.mugloar.exception.GameNotFoundException;
import com.mugloar.dragons.mugloar.exception.GameOverException;
import com.mugloar.dragons.mugloar.exception.InvalidActionException;
import com.mugloar.dragons.mugloar.exception.MugloarException;
import com.mugloar.dragons.mugloar.exception.MugloarProtocolException;
import com.mugloar.dragons.mugloar.exception.MugloarRateLimitedException;
import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import java.util.EnumSet;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GameController.class)
class GameControllerTest {

    private static final String GAME_ID = "IeLKvlDb";
    private static final String AD_ID = "LTyNBlYB";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GameService games;

    private static GameState state(int lives) {
        return new GameState(GAME_ID, lives, 42, 3, 137, 9);
    }

    private static EnrichedAd enrichedAd() {
        return new EnrichedAd(AD_ID, "Help someone", 180, 1, true, "Piece of cake",
                Probability.PIECE_OF_CAKE, 0.123456, 22.2222,
                EnumSet.of(AdFlag.OUT_OF_LEAGUE, AdFlag.EXPIRING_NEXT_TURN));
    }

    @Test
    void startingAGameReturns201AndTheWholeState() throws Exception {
        when(games.startGame()).thenReturn(new GameState(GAME_ID, 3, 0, 0, 0, 0));

        mockMvc.perform(post("/api/games"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.gameId").value(GAME_ID))
                .andExpect(jsonPath("$.lives").value(3))
                .andExpect(jsonPath("$.gold").value(0))
                .andExpect(jsonPath("$.level").value(0))
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.turn").value(0))
                .andExpect(jsonPath("$.finished").value(false));
    }

    @Test
    void theBoardCarriesDecodedAdsAndTheStateTheyWereScoredAgainst() throws Exception {
        when(games.listAds(GAME_ID)).thenReturn(new AdBoard(state(3), List.of(enrichedAd())));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.level").value(3))
                .andExpect(jsonPath("$.ads[0].adId").value(AD_ID))
                .andExpect(jsonPath("$.ads[0].probability").value("Piece of cake"))
                .andExpect(jsonPath("$.ads[0].probabilityTier").value("SAFE"))
                .andExpect(jsonPath("$.ads[0].encrypted").value(true))
                .andExpect(jsonPath("$.ads[0].flags").isArray())
                .andExpect(jsonPath("$.ads[0].flags",
                        Matchers.containsInAnyOrder("EXPIRING_NEXT_TURN", "OUT_OF_LEAGUE")));
    }

    @Test
    void roundsTheEstimatesOnTheWayOut() throws Exception {
        when(games.listAds(GAME_ID)).thenReturn(new AdBoard(state(3), List.of(enrichedAd())));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(jsonPath("$.ads[0].successProbability").value(0.1235))
                .andExpect(jsonPath("$.ads[0].expectedValue").value(22.2222));
    }

    @Test
    void solvingReturnsTheOutcomeAndTheNewState() throws Exception {
        when(games.solve(GAME_ID, AD_ID)).thenReturn(
                new SolveOutcome(state(2), AD_ID, false, "You failed on the mission!"));

        mockMvc.perform(post("/api/games/{gameId}/ads/{adId}/solve", GAME_ID, AD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adId").value(AD_ID))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You failed on the mission!"))
                .andExpect(jsonPath("$.game.lives").value(2));
    }

    @Test
    void reportsAFinishedGameOnTheStateItself() throws Exception {
        when(games.solve(GAME_ID, AD_ID)).thenReturn(
                new SolveOutcome(state(0), AD_ID, false, "You were defeated on your last mission!"));

        mockMvc.perform(post("/api/games/{gameId}/ads/{adId}/solve", GAME_ID, AD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.game.finished").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "has space",
            "dotted.id",
            "0123456789012345678901234567890123456789012345678901234567890123456789"})
    void rejectsAnIdThatCouldNeverBeAGameIdWithoutCallingAnything(String gameId) throws Exception {
        mockMvc.perform(get("/api/games/{gameId}/ads", gameId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(games, never()).listAds(anyString());
    }

    @Test
    void rejectsAMalformedAdIdWithoutCallingAnything() throws Exception {
        mockMvc.perform(post("/api/games/{gameId}/ads/{adId}/solve", GAME_ID, "no good"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(games, never()).solve(anyString(), anyString());
    }

    @Test
    void errorsAreProblemJsonCarryingACodeAndADetail() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(new SessionExpiredException(GAME_ID));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void neverLeaksAnUpstreamErrorBodyToTheClient() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(
                new InvalidActionException("Mugloar rejected the action: <html><pre>Bad Request</pre>"));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ACTION"))
                .andExpect(jsonPath("$.detail", Matchers.not(Matchers.containsString("html"))));
    }

    @Test
    void ourOwnGameOverGuardAndTheUpstreamOneShareACode() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(new GameNotRunningException(GAME_ID));
        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GAME_OVER"));

        when(games.solve(GAME_ID, AD_ID)).thenThrow(new GameOverException("The game is over"));
        mockMvc.perform(post("/api/games/{gameId}/ads/{adId}/solve", GAME_ID, AD_ID))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GAME_OVER"));
    }

    @Test
    void mapsAStaleAdToAConflict() throws Exception {
        when(games.solve(GAME_ID, AD_ID)).thenThrow(new AdNotAvailableException(AD_ID));

        mockMvc.perform(post("/api/games/{gameId}/ads/{adId}/solve", GAME_ID, AD_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AD_NOT_AVAILABLE"));
    }

    @Test
    void mapsAnUnknownGameUpstreamToNotFound() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(new GameNotFoundException("gone"));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    void mapsAnUnreachableUpstreamToServiceUnavailable() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(
                new MugloarUnavailableException("down", null, new RuntimeException()));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"));
    }

    @Test
    void mapsAnUnreadableUpstreamBodyToBadGateway() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(
                new MugloarProtocolException("garbage", null, new RuntimeException()));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_PROTOCOL"));
    }

    /** Passed through as a 429 rather than a 502: the pace is the problem, not the gateway. */
    @Test
    void mapsUpstreamRateLimitingToTooManyRequests() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(
                new MugloarRateLimitedException("Error 1015: You are being rate limited"));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("UPSTREAM_RATE_LIMITED"));
    }

    @Test
    void mapsAnyOtherUpstreamFailureToBadGateway() throws Exception {
        when(games.listAds(GAME_ID)).thenThrow(new MugloarException("cloudflare said no", 403));

        mockMvc.perform(get("/api/games/{gameId}/ads", GAME_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"));
    }

    /**
     * The client branches on {@code code} and never on a status, so a response without one would
     * be unreadable to it. These are the failures Spring raises before a controller is reached.
     */
    @Test
    void anUnknownPathStillCarriesACode() throws Exception {
        mockMvc.perform(get("/api/games/{gameId}/nonsense", GAME_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void aMethodTheEndpointDoesNotTakeStillCarriesACode() throws Exception {
        mockMvc.perform(get("/api/games/{gameId}/ads/{adId}/solve", GAME_ID, AD_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(games, never()).solve(anyString(), anyString());
    }
}
