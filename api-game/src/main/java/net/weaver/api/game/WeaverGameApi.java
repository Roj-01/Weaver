package net.weaver.api.game;

/**
 * Version marker for the optional typed game API.
 *
 * <p>The version tracks the Minecraft game version the typed artifact is
 * compiled against. It is separate from {@link net.weaver.api.WeaverApi#VERSION}
 * so the game-typed artifact can be rebuilt for a new Minecraft release without
 * changing the game-independent API version.
 */
public final class WeaverGameApi {

    /** The Minecraft version this typed artifact targets. */
    public static final String VERSION = "26.3";

    private WeaverGameApi() {
    }
}
