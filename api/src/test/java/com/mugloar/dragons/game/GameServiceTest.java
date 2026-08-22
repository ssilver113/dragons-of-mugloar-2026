package com.mugloar.dragons.game;

import com.mugloar.dragons.ads.AdEnricher;
import com.mugloar.dragons.ads.AdFlag;
import com.mugloar.dragons.ads.EnrichedAd;
import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.MugloarUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServiceTest {

    private static final String GAME_ID = "IeLKvlDb";

    private MugloarClient client;
    private GameSessionRegistry sessions;
    private GameService service;

    @BeforeEach
    void setUp() {
        client = mock(MugloarClient.class);
        sessions = new GameSessionRegistry(
                new GameProperties(Duration.ofMinutes(30)), Clock.systemUTC());
        service = new GameService(client, new AdEnricher(SuccessModel.MEASURED), sessions);
    }

    private GameState startGame() {
        when(client.startGame()).thenReturn(new GameStartedResponse(GAME_ID, 3, 0, 0, 0, 0));
        return service.startGame();
    }

    private static AdResponse ad(String adId, int reward, String probability) {
        return new AdResponse(adId, "Help someone", reward, 5, null, probability);
    }

    @Test
    void startingAGameRegistersItsSession() {
        GameState state = startGame();

        assertThat(state).isEqualTo(new GameState(GAME_ID, 3, 0, 0, 0, 0));
        assertThat(sessions.require(GAME_ID).state()).isEqualTo(state);
    }

    @Test
    void listsTheBoardScoredAgainstTheLevelWeAreTracking() {
        startGame();
        sessions.require(GAME_ID).setState(new GameState(GAME_ID, 3, 20, 12, 400, 9));
        when(client.listAds(GAME_ID)).thenReturn(List.of(ad("LTyNBlYB", 180, "Piece of cake")));

        AdBoard board = service.listAds(GAME_ID);

        assertThat(board.game().level()).isEqualTo(12);
        assertThat(board.ads()).singleElement()
                .returns(180, EnrichedAd::reward)
                .extracting(EnrichedAd::flags, org.assertj.core.api.InstanceOfAssertFactories.iterable(AdFlag.class))
                .doesNotContain(AdFlag.OUT_OF_LEAGUE);
    }

    @Test
    void refusesAnythingWithoutALiveSession() {
        assertThatThrownBy(() -> service.listAds(GAME_ID)).isInstanceOf(SessionExpiredException.class);
        assertThatThrownBy(() -> service.solve(GAME_ID, "LTyNBlYB"))
                .isInstanceOf(SessionExpiredException.class);
        verify(client, never()).listAds(anyString());
    }

    @Test
    void solvingCarriesTheTrackedLevelForwardBecauseTheResponseOmitsIt() {
        startGame();
        sessions.require(GAME_ID).setState(new GameState(GAME_ID, 3, 0, 7, 120, 8));
        when(client.solve(GAME_ID, "LTyNBlYB"))
                .thenReturn(new SolveResponse(true, 3, 17, 137, 9, "You successfully solved the mission!"));

        SolveOutcome outcome = service.solve(GAME_ID, "LTyNBlYB");

        assertThat(outcome.game()).isEqualTo(new GameState(GAME_ID, 3, 17, 7, 137, 9));
        assertThat(outcome.success()).isTrue();
        assertThat(sessions.require(GAME_ID).state().level()).isEqualTo(7);
    }

    @Test
    void solvingSendsTheDecodedIdUpstream() {
        startGame();
        when(client.listAds(GAME_ID)).thenReturn(List.of(new AdResponse(
                "c2hFRmNq", "SGVscCBTaGVsYSBUb3duc2VuZCB0byBmaW5kIGEgbG9zdCBjYXQ=",
                42, 5, 1, "UXVpdGUgbGlrZWx5")));
        when(client.solve(GAME_ID, "shEFcj"))
                .thenReturn(new SolveResponse(true, 3, 42, 42, 1, "You successfully solved the mission!"));

        String adId = service.listAds(GAME_ID).ads().getFirst().adId();
        service.solve(GAME_ID, adId);

        verify(client).solve(GAME_ID, "shEFcj");
    }

    @Test
    void refusesAnAdThatIsNotOnTheBoardWeLastFetched() {
        startGame();
        when(client.listAds(GAME_ID)).thenReturn(List.of(ad("LTyNBlYB", 20, "Sure thing")));
        service.listAds(GAME_ID);

        assertThatThrownBy(() -> service.solve(GAME_ID, "ZZZZZZZZ"))
                .isInstanceOf(AdNotAvailableException.class);
        verify(client, never()).solve(anyString(), anyString());
    }

    @Test
    void refusesToSolveTheSameAdTwiceEvenAfterTheBoardIsRefetched() {
        startGame();
        when(client.listAds(GAME_ID)).thenReturn(List.of(ad("LTyNBlYB", 20, "Sure thing")));
        when(client.solve(GAME_ID, "LTyNBlYB"))
                .thenReturn(new SolveResponse(true, 3, 20, 20, 1, "You successfully solved the mission!"));
        service.listAds(GAME_ID);
        service.solve(GAME_ID, "LTyNBlYB");
        service.listAds(GAME_ID);

        assertThatThrownBy(() -> service.solve(GAME_ID, "LTyNBlYB"))
                .isInstanceOf(AdNotAvailableException.class);
        verify(client, times(1)).solve(GAME_ID, "LTyNBlYB");
    }

    @Test
    void neverReattemptsASolveThatFailedInFlight() {
        startGame();
        when(client.listAds(GAME_ID)).thenReturn(List.of(ad("LTyNBlYB", 20, "Sure thing")));
        when(client.solve(GAME_ID, "LTyNBlYB"))
                .thenThrow(new MugloarUnavailableException("upstream died", null));
        service.listAds(GAME_ID);

        assertThatThrownBy(() -> service.solve(GAME_ID, "LTyNBlYB"))
                .isInstanceOf(MugloarUnavailableException.class);
        assertThatThrownBy(() -> service.solve(GAME_ID, "LTyNBlYB"))
                .isInstanceOf(AdNotAvailableException.class);
        verify(client, times(1)).solve(GAME_ID, "LTyNBlYB");
    }

    @Test
    void letsTheUpstreamDecideWhenNoBoardHasBeenFetchedYet() {
        startGame();
        when(client.solve(GAME_ID, "LTyNBlYB"))
                .thenReturn(new SolveResponse(true, 3, 20, 20, 1, "You successfully solved the mission!"));

        assertThat(service.solve(GAME_ID, "LTyNBlYB").success()).isTrue();
    }

    @Test
    void allowsAHopelessAdThroughBecauseThatIsThePlayersCallToMake() {
        startGame();
        when(client.listAds(GAME_ID)).thenReturn(List.of(ad("LTyNBlYB", 500, "Impossible")));
        when(client.solve(GAME_ID, "LTyNBlYB"))
                .thenReturn(new SolveResponse(false, 2, 0, 0, 1, "You failed on the mission!"));
        service.listAds(GAME_ID);

        assertThat(service.solve(GAME_ID, "LTyNBlYB").success()).isFalse();
    }

    @Test
    void refusesEveryActionOnceTheLastLifeIsGone() {
        startGame();
        when(client.listAds(GAME_ID)).thenReturn(List.of(ad("LTyNBlYB", 20, "Sure thing")));
        when(client.solve(GAME_ID, "LTyNBlYB"))
                .thenReturn(new SolveResponse(false, 0, 0, 0, 1, "You were defeated on your last mission!"));
        service.listAds(GAME_ID);
        service.solve(GAME_ID, "LTyNBlYB");

        assertThat(sessions.require(GAME_ID).state().finished()).isTrue();
        assertThatThrownBy(() -> service.listAds(GAME_ID)).isInstanceOf(GameNotRunningException.class);
        assertThatThrownBy(() -> service.solve(GAME_ID, "LTyNBlYB"))
                .isInstanceOf(GameNotRunningException.class);
        assertThatThrownBy(() -> service.passTurn(GAME_ID))
                .isInstanceOf(GameNotRunningException.class);
    }

    /**
     * The reputation response carries no state, so the turn is applied locally. Verified against
     * the live API: every ad aged by one and lives, gold, level and score were untouched.
     */
    @Test
    void passingSpendsATurnAndMovesNothingElse() {
        startGame();
        sessions.require(GAME_ID).setState(new GameState(GAME_ID, 2, 140, 3, 400, 9));
        when(client.investigateReputation(GAME_ID)).thenReturn(new ReputationResponse(0, 0, 0));

        assertThat(service.passTurn(GAME_ID)).isEqualTo(new GameState(GAME_ID, 2, 140, 3, 400, 10));
        assertThat(sessions.require(GAME_ID).state().turn()).isEqualTo(10);
        verify(client, never()).solve(anyString(), anyString());
    }
}
