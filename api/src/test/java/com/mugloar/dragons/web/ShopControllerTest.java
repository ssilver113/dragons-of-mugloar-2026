package com.mugloar.dragons.web;

import com.mugloar.dragons.game.GameNotRunningException;
import com.mugloar.dragons.game.GameState;
import com.mugloar.dragons.game.SessionExpiredException;
import com.mugloar.dragons.shop.InsufficientGoldException;
import com.mugloar.dragons.shop.ItemEffect;
import com.mugloar.dragons.shop.ItemNotAvailableException;
import com.mugloar.dragons.shop.PurchaseOutcome;
import com.mugloar.dragons.shop.ShopCatalogue;
import com.mugloar.dragons.shop.ShopItem;
import com.mugloar.dragons.shop.ShopService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShopController.class)
class ShopControllerTest {

    private static final String GAME_ID = "IeLKvlDb";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopService shop;

    private static GameState state(int gold) {
        return new GameState(GAME_ID, 3, gold, 2, 400, 9);
    }

    @Test
    void listsItemsWithTheirEffectAsNumbersTheClientCanApply() throws Exception {
        when(shop.listItems(GAME_ID)).thenReturn(new ShopCatalogue(state(120), List.of(
                new ShopItem("hpot", "Healing potion", 50, ItemEffect.EXTRA_LIFE),
                new ShopItem("wingpotmax", "Potion of Awesome Wings", 300, ItemEffect.DOUBLE_LEVEL_UP))));

        mockMvc.perform(get("/api/games/{gameId}/shop", GAME_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.game.gold").value(120))
                .andExpect(jsonPath("$.items[0].id").value("hpot"))
                .andExpect(jsonPath("$.items[0].livesGained").value(1))
                .andExpect(jsonPath("$.items[0].levelsGained").value(0))
                .andExpect(jsonPath("$.items[1].cost").value(300))
                .andExpect(jsonPath("$.items[1].levelsGained").value(2));
    }

    @Test
    void buyingReturnsTheOutcomeAndTheNewState() throws Exception {
        when(shop.buy(GAME_ID, "cs")).thenReturn(new PurchaseOutcome(state(20), "cs", true));

        mockMvc.perform(post("/api/games/{gameId}/shop/{itemId}/buy", GAME_ID, "cs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value("cs"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.game.gold").value(20));
    }

    /** A refusal upstream cost a turn, so it is a 200 with the new state, not an error. */
    @Test
    void reportsAShopRefusalAsASuccessfulRequestWithTheTurnSpent() throws Exception {
        when(shop.buy(GAME_ID, "cs")).thenReturn(new PurchaseOutcome(state(120), "cs", false));

        mockMvc.perform(post("/api/games/{gameId}/shop/{itemId}/buy", GAME_ID, "cs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.game.turn").value(9));
    }

    @Test
    void mapsAnUnstockedItemToAConflict() throws Exception {
        when(shop.buy(GAME_ID, "sword")).thenThrow(new ItemNotAvailableException("sword"));

        mockMvc.perform(post("/api/games/{gameId}/shop/{itemId}/buy", GAME_ID, "sword"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ITEM_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void mapsAnUnaffordableItemToAConflictWithoutLeakingThePrice() throws Exception {
        when(shop.buy(GAME_ID, "wingpotmax"))
                .thenThrow(new InsufficientGoldException("wingpotmax", 300, 120));

        mockMvc.perform(post("/api/games/{gameId}/shop/{itemId}/buy", GAME_ID, "wingpotmax"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_GOLD"));
    }

    @Test
    void mapsAFinishedGameToGone() throws Exception {
        when(shop.listItems(GAME_ID)).thenThrow(new GameNotRunningException(GAME_ID));

        mockMvc.perform(get("/api/games/{gameId}/shop", GAME_ID))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GAME_OVER"));
    }

    @Test
    void mapsALostSessionToNotFound() throws Exception {
        when(shop.listItems(GAME_ID)).thenThrow(new SessionExpiredException(GAME_ID));

        mockMvc.perform(get("/api/games/{gameId}/shop", GAME_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "dotted.id", "0123456789012345678901234567890123456789012345678901234567890123456789"})
    void rejectsAMalformedItemIdWithoutCallingAnything(String itemId) throws Exception {
        mockMvc.perform(post("/api/games/{gameId}/shop/{itemId}/buy", GAME_ID, itemId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(shop, never()).buy(anyString(), anyString());
    }

    @Test
    void rejectsAMalformedGameIdWithoutCallingAnything() throws Exception {
        mockMvc.perform(get("/api/games/{gameId}/shop", "not a game"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(shop, never()).listItems(anyString());
    }
}
