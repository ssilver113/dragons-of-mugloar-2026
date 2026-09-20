package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.mugloar.MugloarMode;
import com.mugloar.dragons.solver.StrategyParameters;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.info.BuildProperties;

import java.time.Instant;

/**
 * What the client needs to know about the server before a game exists.
 *
 * <p>Properties of the deployment, not of a game, so folding them into {@link GameView} would
 * repeat three constants on every response. The build stamp comes from the running jar rather than
 * the bundle, because the failure it exposes is an older process still holding the port.
 *
 * @param offline true when the game is simulated, so the player is never told a score means
 *                something it does not
 * @param lifeValueGold what the solver holds a life to be worth, before the lives in hand divide
 *                      it. The advisor's balanced posture is this figure, so its ordering is
 *                      provably the bot's rather than the same number typed twice
 * @param version the running build's version, absent when the jar carries no build information
 * @param builtAt when that build was assembled, absent on the same terms
 */
public record MetaView(
        boolean offline, double lifeValueGold, @Nullable String version, @Nullable Instant builtAt) {

    public static MetaView from(
            MugloarMode mode, StrategyParameters strategy, @Nullable BuildProperties build) {
        return new MetaView(
                mode == MugloarMode.OFFLINE,
                strategy.lifeValueGold(),
                build == null ? null : build.getVersion(),
                build == null ? null : build.getTime());
    }
}
