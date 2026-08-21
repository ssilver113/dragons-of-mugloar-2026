package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.game.AdBoard;
import java.util.List;

/** Response of {@code GET /api/games/{gameId}/ads}. Order is the upstream's; the client sorts. */
public record AdBoardView(GameView game, List<AdView> ads) {

    public static AdBoardView from(AdBoard board) {
        return new AdBoardView(
                GameView.from(board.game()),
                board.ads().stream().map(AdView::from).toList());
    }
}
