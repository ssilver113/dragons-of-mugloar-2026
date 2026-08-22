package com.mugloar.dragons.solver;

import com.mugloar.dragons.ads.AdEnricher;
import com.mugloar.dragons.ads.AdFlag;
import com.mugloar.dragons.ads.EnrichedAd;
import com.mugloar.dragons.ads.Probability;
import com.mugloar.dragons.ads.SuccessModel;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.mugloar.dto.AdResponse;
import com.mugloar.dragons.shop.ItemEffect;
import com.mugloar.dragons.shop.ShopItem;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAdjustedStrategyTest {

    private static final String GAME_ID = "IeLKvlDb";

    /** The shop as recon found it: one potion, five identical level items, five identical doubles. */
    private static final List<ShopItem> SHOP = List.of(
            new ShopItem("hpot", "Healing potion", 50, ItemEffect.EXTRA_LIFE),
            new ShopItem("gas", "Gasoline", 100, ItemEffect.LEVEL_UP),
            new ShopItem("cs", "Claw Sharpening", 100, ItemEffect.LEVEL_UP),
            new ShopItem("wingpotmax", "Potion of Awesome Wings", 300, ItemEffect.DOUBLE_LEVEL_UP),
            new ShopItem("ch", "Copper Plate Mail", 300, ItemEffect.DOUBLE_LEVEL_UP));

    private final Strategy strategy = new RiskAdjustedStrategy(StrategyParameters.DEFAULT);

    /** Level 5 at turn 0 is comfortably past the level target, so these boards test ad choice alone. */
    private static GameState state(int lives, int gold) {
        return new GameState(GAME_ID, lives, gold, 5, 400, 0);
    }

    private static EnrichedAd ad(String adId, int reward, double success, int expiresIn) {
        return new EnrichedAd(adId, "Help someone", reward, expiresIn, false, "Piece of cake",
                Probability.PIECE_OF_CAKE, success, reward * success, Set.of());
    }

    private static EnrichedAd doomed(String adId, int reward) {
        return new EnrichedAd(adId, "Slay a god", reward, 5, false, "Suicide mission",
                Probability.SUICIDE_MISSION, 0.0, 0.0, Set.of(AdFlag.NEVER_ATTEMPT));
    }

    private static EnrichedAd unreadable(String adId, int reward) {
        return new EnrichedAd(adId, "ÆØÅ", reward, 5, true, "??",
                Probability.UNRECOGNISED, 0.0, 0.0, Set.of(AdFlag.UNREADABLE));
    }

    private static Verdict verdictFor(Decision decision, String adId) {
        return decision.ads().stream()
                .filter(option -> option.adId().equals(adId))
                .findFirst().orElseThrow().verdict();
    }

    private static Verdict itemVerdictFor(Decision decision, String itemId) {
        return decision.items().stream()
                .filter(option -> option.itemId().equals(itemId))
                .findFirst().orElseThrow().verdict();
    }

    @Test
    void picksTheBestRiskAdjustedAdRatherThanTheBiggestReward() {
        List<EnrichedAd> board = List.of(ad("rich", 200, 0.30, 5), ad("safe", 100, 0.90, 5));

        Decision decision = strategy.decide(state(3, 0), board, SHOP);

        assertThat(decision.move()).isEqualTo(Move.solve("safe"));
        assertThat(decision.reason()).isEqualTo(Reason.BEST_RISK_ADJUSTED_AD);
        assertThat(verdictFor(decision, "rich")).isEqualTo(Verdict.NOT_WORTH_A_LIFE);
    }

    /**
     * The whole point of pricing a life instead of tabulating thresholds: the same board, the same
     * ad, and the posture changes because the last life costs three times what one of three does.
     */
    @Test
    void refusesAtOneLifeTheGambleItTakesAtThree() {
        List<EnrichedAd> board = List.of(ad("coinflip", 150, 0.55, 5));

        assertThat(strategy.decide(state(3, 0), board, SHOP).move()).isEqualTo(Move.solve("coinflip"));

        Decision desperate = strategy.decide(state(1, 0), board, SHOP);
        assertThat(desperate.move().type()).isEqualTo(MoveType.INVESTIGATE_REPUTATION);
        assertThat(verdictFor(desperate, "coinflip")).isEqualTo(Verdict.NOT_WORTH_A_LIFE);
    }

    @Test
    void neverAttemptsADoomedAdHoweverLargeTheReward() {
        Decision decision = strategy.decide(
                state(3, 0), List.of(doomed("suicide", 5000), ad("modest", 40, 0.87, 5)), SHOP);

        assertThat(decision.move()).isEqualTo(Move.solve("modest"));
        assertThat(verdictFor(decision, "suicide")).isEqualTo(Verdict.NEVER_ATTEMPT);
    }

    @Test
    void neverAttemptsAnAdItCouldNotRead() {
        Decision decision = strategy.decide(state(3, 0), List.of(unreadable("garbled", 300)), SHOP);

        assertThat(decision.move().type()).isEqualTo(MoveType.INVESTIGATE_REPUTATION);
        assertThat(verdictFor(decision, "garbled")).isEqualTo(Verdict.UNREADABLE);
    }

    /** The one with turns left will still be there next turn; the other will not. */
    @Test
    void breaksATieTowardsTheAdThatExpiresFirst() {
        List<EnrichedAd> board = List.of(ad("patient", 100, 0.87, 5), ad("expiring", 100, 0.87, 1));

        Decision decision = strategy.decide(state(3, 0), board, SHOP);

        assertThat(decision.move()).isEqualTo(Move.solve("expiring"));
        assertThat(decision.ads()).extracting(AdOption::adId).containsExactly("expiring", "patient");
    }

    @Test
    void healsBeforeSolvingWhenDownToTheLastLife() {
        Decision decision = strategy.decide(state(1, 100), List.of(ad("great", 200, 0.95, 5)), SHOP);

        assertThat(decision.move()).isEqualTo(Move.buy("hpot"));
        assertThat(decision.reason()).isEqualTo(Reason.HEALING_LOW_ON_LIVES);
        assertThat(verdictFor(decision, "great")).isEqualTo(Verdict.OUTRANKED);
    }

    @Test
    void leavesThePotionAloneWhileLivesAreComfortable() {
        Decision decision = strategy.decide(state(3, 100), List.of(ad("great", 200, 0.95, 5)), SHOP);

        assertThat(decision.move()).isEqualTo(Move.solve("great"));
        assertThat(itemVerdictFor(decision, "hpot")).isEqualTo(Verdict.NOT_NEEDED);
    }

    /** Levelling outranks a perfectly good ad: the board's reward scale climbs whether we do or not. */
    @Test
    void levelsUpBeforeSolvingWhenBehindTheTarget() {
        GameState behind = new GameState(GAME_ID, 3, 100, 0, 400, 0);

        Decision decision = strategy.decide(behind, List.of(ad("great", 200, 0.95, 5)), SHOP);

        assertThat(decision.move()).isEqualTo(Move.buy("cs"));
        assertThat(decision.reason()).isEqualTo(Reason.LEVELLING_BEHIND_TARGET);
        assertThat(itemVerdictFor(decision, "ch")).isEqualTo(Verdict.UNAFFORDABLE);
    }

    /** Cheap tier while gold binds, dear tier once it stops — 1.5× the level per gold against 2× per turn. */
    @Test
    void switchesToTheDearTierOnlyOnceGoldStopsBeingTheConstraint() {
        List<EnrichedAd> board = List.of(ad("great", 200, 0.95, 5));

        Decision thrifty = strategy.decide(new GameState(GAME_ID, 3, 300, 0, 400, 0), board, SHOP);
        assertThat(thrifty.move()).isEqualTo(Move.buy("cs"));
        assertThat(itemVerdictFor(thrifty, "ch")).isEqualTo(Verdict.OUTRANKED);

        Decision flush = strategy.decide(new GameState(GAME_ID, 3, 600, 0, 400, 0), board, SHOP);
        assertThat(flush.move()).isEqualTo(Move.buy("ch"));
        assertThat(itemVerdictFor(flush, "cs")).isEqualTo(Verdict.OUTRANKED);
    }

    /** The level target grows with the turn count, so a dragon that stood still falls behind. */
    @Test
    void theLevelTargetClimbsWithTheTurnCount() {
        GameState late = new GameState(GAME_ID, 3, 100, 5, 400, 40);

        Decision decision = strategy.decide(late, List.of(ad("great", 200, 0.95, 5)), SHOP);

        assertThat(decision.move()).isEqualTo(Move.buy("cs"));
        assertThat(decision.reason()).isEqualTo(Reason.LEVELLING_BEHIND_TARGET);
    }

    @Test
    void spendsADeadBoardsTurnOnALevelWhenItCanAffordOne() {
        Decision decision = strategy.decide(state(3, 100), List.of(doomed("suicide", 900)), SHOP);

        assertThat(decision.move()).isEqualTo(Move.buy("cs"));
        assertThat(decision.reason()).isEqualTo(Reason.LEVELLING_NO_AD_WORTH_A_LIFE);
    }

    @Test
    void buysALifeWhenNothingIsWorthOneAndLevellingIsOutOfReach() {
        Decision decision = strategy.decide(state(3, 50), List.of(doomed("suicide", 900)), SHOP);

        assertThat(decision.move()).isEqualTo(Move.buy("hpot"));
        assertThat(decision.reason()).isEqualTo(Reason.HEALING_NO_AD_WORTH_A_LIFE);
    }

    @Test
    void passesRatherThanGamblesWhenItCanAffordNothing() {
        Decision decision = strategy.decide(state(3, 20), List.of(doomed("suicide", 900)), SHOP);

        assertThat(decision.move().type()).isEqualTo(MoveType.INVESTIGATE_REPUTATION);
        assertThat(decision.move().targetId()).isNull();
        assertThat(decision.reason()).isEqualTo(Reason.PASSING_NOTHING_WORTH_A_TURN);
        assertThat(decision.items()).allSatisfy(
                item -> assertThat(item.verdict()).isEqualTo(Verdict.UNAFFORDABLE));
    }

    /** The log renders this list, so nothing the solver looked at may go missing from it. */
    @Test
    void everyAdOnTheBoardComesBackRankedAndWithAVerdict() {
        List<EnrichedAd> board = List.of(
                doomed("suicide", 900), ad("weak", 20, 0.40, 5),
                unreadable("garbled", 80), ad("strong", 120, 0.90, 5));

        Decision decision = strategy.decide(state(3, 0), board, SHOP);

        assertThat(decision.ads()).extracting(AdOption::adId)
                .containsExactly("strong", "weak", "garbled", "suicide");
        assertThat(decision.ads()).extracting(AdOption::verdict).containsExactly(
                Verdict.CHOSEN, Verdict.NOT_WORTH_A_LIFE, Verdict.UNREADABLE, Verdict.NEVER_ATTEMPT);
        assertThat(decision.items()).extracting(ItemOption::itemId)
                .containsExactly("hpot", "cs", "ch");
    }

    /**
     * The trap recon proved fatal, scored through the real model rather than a hand-picked
     * probability: a {@code Piece of cake} worth 180 is a certain loss at level 0 and a good bet at
     * level 12. Same ad, same label, opposite call.
     */
    @Test
    void refusesTheHighRewardTrapAtLowLevelAndTakesItAtHigh() {
        AdEnricher enricher = new AdEnricher(SuccessModel.MEASURED);
        AdResponse trap = new AdResponse("trap", "Slay a dragon", 180, 5, null, "Piece of cake");

        Decision novice = strategy.decide(
                new GameState(GAME_ID, 3, 0, 0, 400, 0), List.of(enricher.enrich(trap, 0)), SHOP);
        assertThat(novice.move().type()).isEqualTo(MoveType.INVESTIGATE_REPUTATION);
        assertThat(verdictFor(novice, "trap")).isEqualTo(Verdict.NOT_WORTH_A_LIFE);

        Decision veteran = strategy.decide(
                new GameState(GAME_ID, 3, 0, 12, 400, 0), List.of(enricher.enrich(trap, 12)), SHOP);
        assertThat(veteran.move()).isEqualTo(Move.solve("trap"));
    }

    @Test
    void aLifeIsWorthMoreTheFewerAreLeft() {
        StrategyParameters parameters = StrategyParameters.DEFAULT;

        assertThat(parameters.lifeCost(3)).isEqualTo(100.0);
        assertThat(parameters.lifeCost(1)).isEqualTo(300.0);
        assertThat(parameters.lifeCost(0)).isEqualTo(300.0);
    }
}
