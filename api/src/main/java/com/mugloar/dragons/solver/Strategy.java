package com.mugloar.dragons.solver;

import com.mugloar.dragons.ads.EnrichedAd;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.shop.ShopItem;
import java.util.List;

/**
 * Picks one move per turn.
 *
 * <p>Deliberately pure: state in, decision out, no client and no side effects. Executing the move
 * and paying for it is {@link AutoPlayService}'s job, which is what lets the whole strategy be
 * tested against fixed boards with no test double at all.
 */
public interface Strategy {

    Decision decide(GameState game, List<EnrichedAd> board, List<ShopItem> shop);
}
