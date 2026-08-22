package com.mugloar.dragons.web;

import com.mugloar.dragons.game.GameNotRunningException;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.game.Reputation;
import com.mugloar.dragons.solver.AdOption;
import com.mugloar.dragons.solver.AutoPlayService;
import com.mugloar.dragons.solver.AutoPlayStep;
import com.mugloar.dragons.solver.Decision;
import com.mugloar.dragons.solver.ItemOption;
import com.mugloar.dragons.solver.Move;
import com.mugloar.dragons.solver.Reason;
import com.mugloar.dragons.solver.Verdict;
import java.util.List;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoPlayController.class)
class AutoPlayControllerTest {

    private static final String GAME_ID = "IeLKvlDb";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutoPlayService autoPlay;

    @Test
    void returnsTheNewStateAlongsideEverythingTheSolverWeighed() throws Exception {
        Decision decision = new Decision(
                Move.solve("safe"),
                Reason.BEST_RISK_ADJUSTED_AD,
                List.of(
                        new AdOption("safe", "Help someone", 60, 5, "Piece of cake", "SAFE",
                                0.845678, 71.234567, Verdict.CHOSEN),
                        new AdOption("trap", "Slay a dragon", 400, 3, "Piece of cake", "SAFE",
                                0.0004, -99.5, Verdict.NOT_WORTH_A_LIFE)),
                List.of(new ItemOption("hpot", "Healing potion", 50, 1, 0, Verdict.NOT_NEEDED)));
        when(autoPlay.step(GAME_ID)).thenReturn(AutoPlayStep.of(
                new GameState(GAME_ID, 3, 60, 5, 460, 1),
                decision,
                true,
                "You successfully solved the mission!"));

        mockMvc.perform(post("/api/games/{gameId}/autoplay/step", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.game.score").value(460))
                .andExpect(jsonPath("$.game.turn").value(1))
                .andExpect(jsonPath("$.succeeded").value(true))
                .andExpect(jsonPath("$.message").value("You successfully solved the mission!"))
                .andExpect(jsonPath("$.decision.move").value("SOLVE_AD"))
                .andExpect(jsonPath("$.decision.targetId").value("safe"))
                .andExpect(jsonPath("$.decision.reason").value("BEST_RISK_ADJUSTED_AD"))
                .andExpect(jsonPath("$.decision.ads[0].verdict").value("CHOSEN"))
                .andExpect(jsonPath("$.decision.ads[0].probability").value("Piece of cake"))
                .andExpect(jsonPath("$.decision.ads[0].probabilityTier").value("SAFE"))
                .andExpect(jsonPath("$.decision.ads[0].successProbability").value(0.8457))
                .andExpect(jsonPath("$.decision.ads[0].score").value(71.2346))
                .andExpect(jsonPath("$.decision.ads[1].score").value(-99.5))
                .andExpect(jsonPath("$.decision.ads[1].verdict").value("NOT_WORTH_A_LIFE"))
                .andExpect(jsonPath("$.decision.items[0].livesGained").value(1))
                .andExpect(jsonPath("$.decision.items[0].verdict").value("NOT_NEEDED"));
    }

    /**
     * A pass aims at nothing, and the wire has to say so rather than invent a target. It is also
     * the one move that reports standing, so the figures ride along on the same response.
     */
    @Test
    void reportsAPassWithNoTargetAndNoMessage() throws Exception {
        when(autoPlay.step(GAME_ID)).thenReturn(new AutoPlayStep(
                new GameState(GAME_ID, 1, 20, 3, 460, 12),
                new Decision(Move.investigateReputation(), Reason.PASSING_NOTHING_WORTH_A_TURN,
                        List.of(), List.of()),
                true,
                null,
                new Reputation(12.5, -3.25, 40.123456)));

        mockMvc.perform(post("/api/games/{gameId}/autoplay/step", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.move").value("INVESTIGATE_REPUTATION"))
                .andExpect(jsonPath("$.decision.targetId").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.reputation.people").value(12.5))
                .andExpect(jsonPath("$.reputation.state").value(-3.25))
                .andExpect(jsonPath("$.reputation.underworld").value(40.1235));
    }

    /** Every other move reports no standing, and must not invent a zero for one. */
    @Test
    void omitsStandingFromEveryMoveThatDidNotBuyIt() throws Exception {
        when(autoPlay.step(GAME_ID)).thenReturn(AutoPlayStep.of(
                new GameState(GAME_ID, 3, 10, 4, 500, 6),
                new Decision(Move.buy("hpot"), Reason.HEALING_LOW_ON_LIVES, List.of(), List.of()),
                true,
                null));

        mockMvc.perform(post("/api/games/{gameId}/autoplay/step", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reputation").doesNotExist());
    }

    @Test
    void aFinishedGameIsTerminalRatherThanRetryable() throws Exception {
        when(autoPlay.step(GAME_ID)).thenThrow(new GameNotRunningException(GAME_ID));

        mockMvc.perform(post("/api/games/{gameId}/autoplay/step", GAME_ID))
                .andExpect(status().isGone())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.GAME_OVER.name()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "has space",
            "dotted.id",
            "0123456789012345678901234567890123456789012345678901234567890123456789"})
    void refusesAGameIdThatCouldNeverBeOneWithoutTouchingTheSolver(String gameId) throws Exception {
        mockMvc.perform(post("/api/games/{gameId}/autoplay/step", gameId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()));

        verify(autoPlay, never()).step(anyString());
    }
}
