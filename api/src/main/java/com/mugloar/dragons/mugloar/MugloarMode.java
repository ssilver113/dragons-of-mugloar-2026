package com.mugloar.dragons.mugloar;

/**
 * Which world the application is playing.
 *
 * <p>Each of the two client configurations contributes the constant it stands for, so the mode a
 * caller reads and the client that is actually wired can never disagree — there is one bean because
 * there is one client.
 */
public enum MugloarMode {

    /** The real game at dragonsofmugloar.com. Every turn is spent for good. */
    LIVE,

    /** A simulated game with no network behind it. Turns are free and the world is not the real one. */
    OFFLINE
}
