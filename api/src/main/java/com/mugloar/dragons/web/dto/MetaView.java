package com.mugloar.dragons.web.dto;

import com.mugloar.dragons.mugloar.MugloarMode;

/**
 * What the client needs to know about the server before a game exists.
 *
 * <p>Only the world it is playing, for now. It is a property of the deployment rather than of any
 * one game, which is why it is not folded into {@link GameView}: threading it through every
 * response that carries state would repeat a constant six times over.
 *
 * @param offline true when the game is simulated, so the player is never told a score means
 *                something it does not
 */
public record MetaView(boolean offline) {

    public static MetaView from(MugloarMode mode) {
        return new MetaView(mode == MugloarMode.OFFLINE);
    }
}
