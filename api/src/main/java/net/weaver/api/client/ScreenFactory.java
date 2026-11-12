package net.weaver.api.client;

/**
 * Creates a client screen on demand.
 *
 * <p>The factory is registered by id through
 * {@link ClientRegistry#registerScreenFactory(ScreenFactory)} and is invoked
 * when a key mapping or {@link ClientRegistry#openScreen(String)} asks for that
 * id. {@link #createScreen(Object, Object)} is called on the client/render
 * thread only.
 *
 * <p>The game objects are typed as {@link Object} so the API module stays
 * game-free. A factory may return {@code null} to cancel opening the screen.
 */
public interface ScreenFactory
{
    /**
     * The unique factory id used by {@link ClientRegistry#openScreen(String)}.
     *
     * @return the non-null, non-blank factory id
     */
    String id();

    /**
     * Creates the screen instance.
     *
     * <p>Called on the client/render thread only. Implementations must not
     * retain the arguments longer than the call unless the game object is known
     * to be long-lived. Returning {@code null} cancels the open request.
     *
     * @param minecraft    the client instance, never {@code null} in a live game
     * @param parentScreen the current parent screen, or {@code null} when there
     *                     is no parent
     * @return the new screen, or {@code null} to cancel
     */
    Object createScreen(Object minecraft, Object parentScreen);
}
