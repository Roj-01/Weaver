package net.weaver.api.game.client.camera;

import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for multiplicative field-of-view modifiers.
 *
 * <p>The built-in {@code CameraRenderMixin} injects at the return of
 * {@code Camera#calculateFov(float)}, composes every active modifier, and
 * replaces the returned value. The composition is multiplicative:
 * {@code result = baseFov * factor1 * factor2 ...}. When the composed value
 * differs from the vanilla value, it is clamped to the inclusive range
 * {@code [1.0, 179.0]} so a broken modifier cannot produce an unusable
 * projection. With no active modifier, or when every factor is a no-op, the
 * vanilla value is returned unchanged.
 *
 * <p>{@code Camera#calculateFov} is the world field-of-view path. The separate
 * HUD field of view computed by {@code Camera#calculateHudFov} is not modified;
 * see {@code docs/client.md}.
 *
 * <p>The registry is thread-safe. Registration may happen from any thread;
 * dispatch runs on the render thread. A listener exception is isolated and
 * reported once per id. A non-finite or non-positive factor is ignored and
 * reported once per id.
 */
public final class FovModifierRegistry
{
    private static final OrderedListenerRegistry<FovModifier> LISTENERS =
            new OrderedListenerRegistry<>("fov modifier");
    private static final Set<String> INVALID_FACTORS = ConcurrentHashMap.newKeySet();

    private FovModifierRegistry()
    {
    }

    /**
     * Registers a field-of-view modifier.
     *
     * @param modifier the modifier, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws net.weaver.api.client.ClientException when the modifier id is
     *                                               blank or already registered
     */
    public static ClientRegistration register(FovModifier modifier)
    {
        return LISTENERS.register(modifier);
    }

    /**
     * Applies every active modifier to the vanilla field of view.
     *
     * <p>Integration entry point for the built-in mixin. Mod code must not call
     * this method directly.
     *
     * @param context the field-of-view snapshot, never {@code null}
     * @param baseFov the vanilla field of view
     * @return the composed and clamped field of view
     */
    @net.weaver.api.annotation.Internal
    public static float apply(FovContext context, float baseFov)
    {
        Objects.requireNonNull(context, "context");
        float[] result = {baseFov};
        boolean[] applied = {false};
        LISTENERS.forEach("FOV MODIFIER", modifier ->
        {
            float factor = modifier.fovMultiplier(context);
            if (!Float.isFinite(factor) || factor <= 0.0F)
            {
                if (INVALID_FACTORS.add(modifier.id()))
                {
                    System.err.println("[weaver] FOV MODIFIER INVALID id=" + modifier.id()
                            + " factor=" + factor);
                }
                return;
            }
            result[0] *= factor;
            applied[0] = true;
        });
        if (!applied[0] || !Float.isFinite(result[0])
                || Float.compare(result[0], baseFov) == 0)
        {
            return baseFov;
        }
        return Math.max(1.0F, Math.min(179.0F, result[0]));
    }

    /**
     * Returns the number of registered modifiers.
     *
     * @return the modifier count
     */
    public static int listenerCount()
    {
        return LISTENERS.listenerCount();
    }

    /**
     * Removes every modifier and clears invalid-value bookkeeping.
     *
     * <p>Intended for tests and loader shutdown.
     */
    public static void reset()
    {
        LISTENERS.reset();
        INVALID_FACTORS.clear();
    }
}
