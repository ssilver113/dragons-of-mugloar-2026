package com.mugloar.dragons.mugloar;

import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.mugloar.dto.GameStartedResponse;
import com.mugloar.dragons.mugloar.dto.PurchaseResponse;
import com.mugloar.dragons.mugloar.dto.ReputationResponse;
import com.mugloar.dragons.mugloar.dto.ShopItemResponse;
import com.mugloar.dragons.mugloar.dto.SolveResponse;
import java.util.List;

/**
 * The one seam between this application and the Mugloar API.
 *
 * <p>Everything above this interface — domain, solver, web layer — is testable without a network,
 * which is the entire reason the boundary exists (D10). Implementations return the wire shapes
 * unchanged; interpreting them, including decoding encrypted ads, belongs further in.
 *
 * <p>Every method throws {@link com.mugloar.dragons.mugloar.exception.MugloarException} or one of
 * its subtypes on failure.
 */
public interface MugloarClient {

    GameStartedResponse startGame();

    List<AdResponse> listAds(String gameId);

    /** Costs a turn, and ages every ad on the board by one. Never retried. */
    SolveResponse solve(String gameId, String adId);

    List<ShopItemResponse> listShopItems(String gameId);

    /**
     * Costs a turn whether or not it succeeds. Never retried.
     *
     * <p>A rejected purchase arrives as HTTP 200 with {@code shoppingSuccess=false}, so callers must
     * read the flag rather than trust the status.
     */
    PurchaseResponse buy(String gameId, String itemId);

    /** Costs a turn. Never retried. */
    ReputationResponse investigateReputation(String gameId);
}
