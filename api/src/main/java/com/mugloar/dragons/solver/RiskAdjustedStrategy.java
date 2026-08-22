package com.mugloar.dragons.solver;

import com.mugloar.dragons.ads.AdFlag;
import com.mugloar.dragons.ads.EnrichedAd;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.shop.ItemEffect;
import com.mugloar.dragons.shop.ShopItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Scores every ad as {@code reward × p − lifeCost × (1 − p)} and spends the turn on the best thing
 * available, buying before solving when the dragon is falling behind.
 *
 * <p>Pricing the risk in gold is what makes the posture emerge instead of being enumerated: the
 * cost of a life is divided by the lives in hand, so a board worth a gamble at three lives is
 * refused at one, with no per-lives table to keep in step with anything.
 *
 * <p>The ladder is: heal when low, level when behind, otherwise take the best ad. If nothing is
 * worth a life the turn still has to go somewhere, so it buys whatever it can afford, and passes
 * only when it can afford nothing — a pass costs a turn and redraws the board, which beats
 * attempting a mission we expect to lose.
 *
 * <p>Levelling comes before solving rather than after because the board's reward scale climbs on
 * its own: recon watched a {@code Piece of cake} decay from 0.95 to 0.04 across fifty turns at a
 * static level 0. Spending is free in score terms — score counts gold earned, not gold held — so
 * the only real cost of a purchase is the turn, and the expiry tick it puts on every ad.
 */
@Component
public class RiskAdjustedStrategy implements Strategy {

    private final StrategyParameters parameters;

    public RiskAdjustedStrategy(StrategyParameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public Decision decide(GameState game, List<EnrichedAd> board, List<ShopItem> shop) {
        List<ScoredAd> ranked = rank(board, game);
        Optional<ScoredAd> best = ranked.stream().filter(ScoredAd::acceptable).findFirst();
        Offers offers = Offers.from(shop);
        int targetLevel = parameters.targetLevel(game.turn());

        Optional<ShopItem> potion = offers.potion().filter(item -> item.cost() <= game.gold());
        Optional<ShopItem> upgrade = affordableUpgrade(offers, game.gold());

        Move move;
        Reason reason;
        if (game.lives() <= parameters.potionThresholdLives() && potion.isPresent()) {
            move = Move.buy(potion.get().id());
            reason = Reason.HEALING_LOW_ON_LIVES;
        } else if (game.level() < targetLevel && upgrade.isPresent()) {
            move = Move.buy(upgrade.get().id());
            reason = Reason.LEVELLING_BEHIND_TARGET;
        } else if (best.isPresent()) {
            move = Move.solve(best.get().ad().adId());
            reason = Reason.BEST_RISK_ADJUSTED_AD;
        } else if (upgrade.isPresent()) {
            move = Move.buy(upgrade.get().id());
            reason = Reason.LEVELLING_NO_AD_WORTH_A_LIFE;
        } else if (potion.isPresent()) {
            move = Move.buy(potion.get().id());
            reason = Reason.HEALING_NO_AD_WORTH_A_LIFE;
        } else {
            move = Move.investigateReputation();
            reason = Reason.PASSING_NOTHING_WORTH_A_TURN;
        }

        return new Decision(
                move,
                reason,
                adOptions(ranked, move),
                itemOptions(offers, game, targetLevel, best.isEmpty(), move));
    }

    /**
     * Best first. Ties break towards the ad that expires soonest, because the one with turns left
     * will still be there next turn and the other will not; the id keeps the order total, so a
     * benchmark run is reproducible.
     */
    private List<ScoredAd> rank(List<EnrichedAd> board, GameState game) {
        double lifeCost = parameters.lifeCost(game.lives());
        return board.stream()
                .map(ad -> new ScoredAd(ad, score(ad, lifeCost)))
                .sorted(Comparator.comparingDouble(ScoredAd::score).reversed()
                        .thenComparingInt(scored -> scored.ad().expiresIn())
                        .thenComparing(scored -> scored.ad().adId()))
                .toList();
    }

    private static double score(EnrichedAd ad, double lifeCost) {
        double success = ad.successProbability();
        return ad.reward() * success - lifeCost * (1 - success);
    }

    private Optional<ShopItem> affordableUpgrade(Offers offers, int gold) {
        Optional<ShopItem> cheap = offers.levelUp().filter(item -> item.cost() <= gold);
        Optional<ShopItem> dear = offers.doubleLevelUp().filter(item -> item.cost() <= gold);
        return gold >= parameters.dearTierGoldFloor() ? dear.or(() -> cheap) : cheap.or(() -> dear);
    }

    private static List<AdOption> adOptions(List<ScoredAd> ranked, Move move) {
        return ranked.stream().map(scored -> new AdOption(
                scored.ad().adId(),
                scored.ad().message(),
                scored.ad().reward(),
                scored.ad().expiresIn(),
                scored.ad().probabilityLabel(),
                scored.ad().successProbability(),
                scored.score(),
                verdict(scored, move))).toList();
    }

    private static Verdict verdict(ScoredAd scored, Move move) {
        if (move.type() == MoveType.SOLVE_AD && move.targets(scored.ad().adId())) {
            return Verdict.CHOSEN;
        }
        if (scored.ad().flags().contains(AdFlag.UNREADABLE)) {
            return Verdict.UNREADABLE;
        }
        if (scored.ad().flags().contains(AdFlag.NEVER_ATTEMPT)) {
            return Verdict.NEVER_ATTEMPT;
        }
        return scored.acceptable() ? Verdict.OUTRANKED : Verdict.NOT_WORTH_A_LIFE;
    }

    private List<ItemOption> itemOptions(
            Offers offers, GameState game, int targetLevel, boolean boardDead, Move move) {
        boolean healthy = game.lives() > parameters.potionThresholdLives();
        boolean levelled = game.level() >= targetLevel;

        List<ItemOption> options = new ArrayList<>(3);
        offers.potion().ifPresent(item ->
                options.add(option(item, move, game.gold(), healthy && !boardDead)));
        offers.levelUp().ifPresent(item ->
                options.add(option(item, move, game.gold(), levelled && !boardDead)));
        offers.doubleLevelUp().ifPresent(item ->
                options.add(option(item, move, game.gold(), levelled && !boardDead)));
        return options;
    }

    private static ItemOption option(ShopItem item, Move move, int gold, boolean unwanted) {
        Verdict verdict;
        if (move.type() == MoveType.BUY_ITEM && move.targets(item.id())) {
            verdict = Verdict.CHOSEN;
        } else if (item.cost() > gold) {
            verdict = Verdict.UNAFFORDABLE;
        } else if (unwanted) {
            verdict = Verdict.NOT_NEEDED;
        } else {
            verdict = Verdict.OUTRANKED;
        }
        return new ItemOption(
                item.id(),
                item.name(),
                item.cost(),
                item.effect().lives(),
                item.effect().levels(),
                verdict);
    }

    private record ScoredAd(EnrichedAd ad, double score) {

        boolean acceptable() {
            return ad.worthAttempting() && score > 0;
        }
    }

    /**
     * The three offers worth weighing. Every 100-gold item is the same purchase and so is every
     * 300-gold one, so the shop's eleven entries collapse to one candidate per effect. An item at a
     * price recon never measured is left out rather than guessed at.
     */
    private record Offers(
            Optional<ShopItem> potion,
            Optional<ShopItem> levelUp,
            Optional<ShopItem> doubleLevelUp) {

        static Offers from(List<ShopItem> shop) {
            return new Offers(
                    cheapest(shop, ItemEffect.EXTRA_LIFE),
                    cheapest(shop, ItemEffect.LEVEL_UP),
                    cheapest(shop, ItemEffect.DOUBLE_LEVEL_UP));
        }

        private static Optional<ShopItem> cheapest(List<ShopItem> shop, ItemEffect effect) {
            return shop.stream()
                    .filter(item -> item.effect() == effect)
                    .min(Comparator.comparingInt(ShopItem::cost).thenComparing(ShopItem::id));
        }
    }
}
