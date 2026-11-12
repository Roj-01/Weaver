package net.weaver.core.client;

import net.weaver.api.client.KeyMappingSpec;

import java.util.List;

/**
 * Game-facing operations used by {@link CoreClientBackend}.
 *
 * <p>Implementations bridge the game-free API values to live client objects.
 * Every game-class reference stays behind this interface so the core module
 * never compiles against {@code net.minecraft} or {@code com.mojang} types.
 */
interface ClientGameAdapter
{
    /**
     * Whether the client game classes are present in this process.
     *
     * @return {@code true} when client operations can be serviced
     */
    boolean isAvailable();

    /**
     * Whether this adapter can detect the client/render thread.
     *
     * <p>The default makes detection follow availability. Implementations that
     * resolve a thread-check member should verify it without initializing game
     * classes.
     *
     * @return {@code true} when thread detection is possible
     */
    default boolean canDetectClientThread()
    {
        return isAvailable();
    }

    /**
     * Throws when the current thread is not the client/render thread.
     *
     * @param operation the operation name included in the failure message
     */
    void assertClientThread(String operation);

    /**
     * Returns the client instance, or {@code null} when it is unavailable.
     *
     * @return the client instance or {@code null}
     */
    Object minecraft();

    /**
     * Returns the currently open screen, or {@code null} when there is none.
     *
     * @return the current screen or {@code null}
     */
    Object currentScreen();

    /**
     * Creates and registers a game key mapping for the supplied spec.
     *
     * @param spec the mapping description, never {@code null}
     * @return the game mapping object, never {@code null}
     */
    Object createKeyMapping(KeyMappingSpec spec);

    /**
     * Removes a game key mapping on a best-effort basis.
     *
     * @param mapping the mapping to remove, possibly {@code null}
     */
    void removeKeyMapping(Object mapping);

    /**
     * Consumes a pending click for the mapping.
     *
     * @param mapping the mapping to poll, never {@code null}
     * @return {@code true} when the mapping was activated
     */
    boolean consumeClick(Object mapping);

    /**
     * Opens the supplied screen.
     *
     * @param screen the screen to open, never {@code null}
     */
    void openScreen(Object screen);

    /**
     * Returns the modifier bits currently held on the client keyboard.
     *
     * <p>The bits use the same values as {@link net.weaver.api.client.KeyModifiers}
     * and Minecraft's {@code InputConstants.MOD_*} constants. Implementations
     * should return {@code 0} when the state cannot be read; the caller treats
     * an unreadable state as "no modifier held" and skips the action.
     *
     * @return the current modifier mask, or {@code 0} when unknown
     */
    default int currentModifiers()
    {
        return 0;
    }

    /**
     * Returns the logical GUI width exposed by the graphics object.
     *
     * @param graphics the graphics object, possibly {@code null}
     * @return the GUI width, or {@code 0} when unknown
     */
    int guiWidth(Object graphics);

    /**
     * Returns the logical GUI height exposed by the graphics object.
     *
     * @param graphics the graphics object, possibly {@code null}
     * @return the GUI height, or {@code 0} when unknown
     */
    int guiHeight(Object graphics);

    /**
     * Converts plain text to a game component.
     *
     * @param text the plain text, never {@code null}
     * @return the game component, or {@code null} when conversion failed
     */
    Object literal(String text);

    /**
     * Appends a game component to a mutable game line list.
     *
     * @param lines     the mutable game line list, never {@code null}
     * @param component the component to append, never {@code null}
     */
    default void appendComponent(List<Object> lines, Object component)
    {
        lines.add(component);
    }
}
