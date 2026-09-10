package com.mugloar.dragons.offline;

import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.mugloar.MugloarClient;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import com.mugloar.dragons.mugloar.exception.GameNotFoundException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * A {@link MugloarClient} with no network behind it, backed by {@link SimulatedWorld}.
 *
 * <p>Same seam and same wire records as {@code RestMugloarClient}, so nothing above the interface
 * can tell which one it is talking to and the offline game exercises the real application.
 * Failures use the same exception types, so the error contract the UI branches on holds here too.
 */
public class SimulatedMugloarClient implements MugloarClient {

    private final Map<String, SimulatedWorld> games;
    private final RandomGenerator seeds;
    private final BoardSource boards;
    private final SuccessModel model;
    private final OfflineProperties properties;

    public SimulatedMugloarClient(
            RandomGenerator seeds,
            BoardSource boards,
            SuccessModel model,
            OfflineProperties properties) {
        this.seeds = seeds;
        this.boards = boards;
        this.model = model;
        this.properties = properties;
        this.games = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SimulatedWorld> eldest) {
                return size() > properties.maxGames();
            }
        });
    }

    @Override
    public GameStartedResponse startGame() {
        String gameId;
        long seed;
        // Each game gets its own stream, drawn from the shared one, so a game replays identically
        // however many others are running beside it.
        synchronized (seeds) {
            gameId = OfflineIds.random(seeds);
            seed = seeds.nextLong();
        }
        SimulatedWorld world =
                new SimulatedWorld(gameId, new Random(seed), boards, model, properties);
        games.put(gameId, world);
        return world.started();
    }

    @Override
    public List<AdResponse> listAds(String gameId) {
        return world(gameId).ads();
    }

    @Override
    public SolveResponse solve(String gameId, String adId) {
        return world(gameId).solve(adId);
    }

    @Override
    public List<ShopItemResponse> listShopItems(String gameId) {
        return world(gameId).shopItems();
    }

    @Override
    public PurchaseResponse buy(String gameId, String itemId) {
        return world(gameId).buy(itemId);
    }

    @Override
    public ReputationResponse investigateReputation(String gameId) {
        return world(gameId).investigate();
    }

    private SimulatedWorld world(String gameId) {
        SimulatedWorld world = games.get(gameId);
        if (world == null) {
            // Either never started, or dropped by the size cap — the upstream's 404 either way.
            throw new GameNotFoundException("No such offline game: " + gameId);
        }
        return world;
    }
}
