package com.mugloar.dragons.web;

import com.mugloar.dragons.game.GameService;
import com.mugloar.dragons.web.dto.AdBoardView;
import com.mugloar.dragons.web.dto.GameView;
import com.mugloar.dragons.web.dto.SolveResultView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@Tag(name = "Game", description = "Start a game, read the board, attempt an ad")
public class GameController {

    private final GameService games;

    public GameController(GameService games) {
        this.games = games;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a new game")
    public GameView startGame() {
        return GameView.from(games.startGame());
    }

    @GetMapping("/{gameId}/ads")
    @Operation(summary = "List the board, decoded and scored for this dragon's level")
    public AdBoardView listAds(@PathVariable @Pattern(regexp = Identifiers.ID_PATTERN) String gameId) {
        return AdBoardView.from(games.listAds(gameId));
    }

    @PostMapping("/{gameId}/ads/{adId}/solve")
    @Operation(summary = "Attempt one ad. Costs a turn and ages every ad on the board")
    public SolveResultView solve(
            @PathVariable @Pattern(regexp = Identifiers.ID_PATTERN) String gameId,
            @PathVariable @Pattern(regexp = Identifiers.ID_PATTERN) String adId) {
        return SolveResultView.from(games.solve(gameId, adId));
    }
}
