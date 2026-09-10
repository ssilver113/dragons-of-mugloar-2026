package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.mugloar.MugloarMode;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.info.BuildProperties;

import java.time.Instant;

/**
 * What the client needs to know about the server before a game exists.
 *
 * <p>Properties of the deployment rather than of any one game, which is why they are not folded
 * into {@link GameView}: threading them through every response that carries state would repeat
 * three constants six times over.
 *
 * <p>The build stamp is read from the running jar rather than compiled into the bundle, so the
 * footer names the server actually answering — the failure it is meant to expose is an older
 * process still holding the port, which a number baked into the frontend could never show.
 *
 * @param offline true when the game is simulated, so the player is never told a score means
 *                something it does not
 * @param version the running build's version, absent when the jar carries no build information
 * @param builtAt when that build was assembled, absent on the same terms
 */
public record MetaView(boolean offline, @Nullable String version, @Nullable Instant builtAt) {

    public static MetaView from(MugloarMode mode, @Nullable BuildProperties build) {
        return new MetaView(
                mode == MugloarMode.OFFLINE,
                build == null ? null : build.getVersion(),
                build == null ? null : build.getTime());
    }
}
