package com.mugloar.dragons.bench;

import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.MugloarRateLimitedException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the real client for the benchmark profile: every call waits its turn at the {@link Pacer},
 * and a rate-limited one widens the gap for everybody before it is rethrown.
 *
 * <p>A decorator rather than pacing inside the client, because only a run of hundreds of games
 * needs managing; a human cannot click fast enough to matter. Nothing is retried here — the
 * client's own policy decides that, and a rate limit is where a second attempt makes things worse.
 */
final class PacedMugloarClient implements MugloarClient {

    private static final Logger log = LoggerFactory.getLogger(PacedMugloarClient.class);

    private final MugloarClient delegate;
    private final Pacer pacer;
    private final AtomicLong calls = new AtomicLong();

    PacedMugloarClient(MugloarClient delegate, Pacer pacer) {
        this.delegate = delegate;
        this.pacer = pacer;
    }

    @Override
    public GameStartedResponse startGame() {
        return paced(delegate::startGame);
    }

    @Override
    public List<AdResponse> listAds(String gameId) {
        return paced(() -> delegate.listAds(gameId));
    }

    @Override
    public SolveResponse solve(String gameId, String adId) {
        return paced(() -> delegate.solve(gameId, adId));
    }

    @Override
    public List<ShopItemResponse> listShopItems(String gameId) {
        return paced(() -> delegate.listShopItems(gameId));
    }

    @Override
    public PurchaseResponse buy(String gameId, String itemId) {
        return paced(() -> delegate.buy(gameId, itemId));
    }

    @Override
    public ReputationResponse investigateReputation(String gameId) {
        return paced(() -> delegate.investigateReputation(gameId));
    }

    long calls() {
        return calls.get();
    }

    private <T> T paced(Supplier<T> call) {
        pacer.awaitTurn();
        calls.incrementAndGet();
        try {
            return call.get();
        } catch (MugloarRateLimitedException e) {
            log.warn("Rate limited — pacing every call {} apart from here on", pacer.slowDown());
            throw e;
        }
    }
}
