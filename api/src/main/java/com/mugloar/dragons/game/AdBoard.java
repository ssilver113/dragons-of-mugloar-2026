package com.mugloar.dragons.game;

import com.mugloar.dragons.ads.EnrichedAd;
import java.util.List;

/** The board as fetched, alongside the state it was scored against. */
public record AdBoard(GameState game, List<EnrichedAd> ads) {
}
