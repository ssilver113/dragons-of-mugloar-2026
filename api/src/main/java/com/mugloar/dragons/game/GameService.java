package com.mugloar.dragons.game;

import com.mugloar.dragons.ads.AdEnricher;
import com.mugloar.dragons.ads.EnrichedAd;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The game use cases, and the only place that decides whether an action is allowed to reach the
 * upstream at all.
 *
 * <p>The guards refuse actions that cannot succeed — a finished game, an unknown session, an ad
 * already attempted — so the player gets a specific reason instead of a generic upstream
 * rejection. They never refuse an action that is merely unwise: which ads are worth solving is the
 * player's call, and the scoring exists to inform it, not to overrule it.
 */
@Service
public class GameService {

    private final MugloarClient client;
    private final AdEnricher enricher;
    private final GameSessionRegistry sessions;

    public GameService(MugloarClient client, AdEnricher enricher, GameSessionRegistry sessions) {
        this.client = client;
        this.enricher = enricher;
        this.sessions = sessions;
    }

    public GameState startGame() {
        GameState state = GameState.from(client.startGame());
        sessions.register(state);
        return state;
    }

    public AdBoard listAds(String gameId) {
        GameSession session = sessions.require(gameId);
        GameState state = requireRunning(session);

        List<EnrichedAd> ads = enricher.enrich(client.listAds(gameId), state.level());
        session.recordBoard(ads.stream().map(EnrichedAd::adId).toList());
        return new AdBoard(state, ads);
    }

    public SolveOutcome solve(String gameId, String adId) {
        GameSession session = sessions.require(gameId);
        GameState state = requireRunning(session);
        if (!session.isKnownSolvable(adId)) {
            throw new AdNotAvailableException(adId);
        }

        // Recorded before the call, not after: a solve is never retried because a request that
        // timed out may already have landed, and the same reasoning forbids a second attempt here.
        session.recordAttempt(adId);
        SolveResponse solved = client.solve(gameId, adId);
        GameState updated = state.afterSolve(solved);
        session.setState(updated);

        return new SolveOutcome(updated, adId, solved.success(), solved.message());
    }

    private static GameState requireRunning(GameSession session) {
        GameState state = session.state();
        if (state.finished()) {
            throw new GameNotRunningException(state.gameId());
        }
        return state;
    }
}
