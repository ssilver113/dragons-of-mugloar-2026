package com.mugloar.dragons.web;

import com.mugloar.dragons.shop.ShopService;
import com.mugloar.dragons.web.dto.PurchaseResultView;
import com.mugloar.dragons.web.dto.ShopView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/shop")
@Tag(name = "Shop", description = "Browse the shop and buy upgrades")
public class ShopController {

    private final ShopService shop;

    public ShopController(ShopService shop) {
        this.shop = shop;
    }

    @GetMapping
    @Operation(summary = "List what is for sale, and what each item does. Costs no turn")
    public ShopView listItems(@PathVariable @Pattern(regexp = Identifiers.ID_PATTERN) String gameId) {
        return ShopView.from(shop.listItems(gameId));
    }

    @PostMapping("/{itemId}/buy")
    @Operation(summary = "Buy one item. Costs a turn, and ages every ad, even if the shop refuses")
    public PurchaseResultView buy(
            @PathVariable @Pattern(regexp = Identifiers.ID_PATTERN) String gameId,
            @PathVariable @Pattern(regexp = Identifiers.ID_PATTERN) String itemId) {
        return PurchaseResultView.from(shop.buy(gameId, itemId));
    }
}
