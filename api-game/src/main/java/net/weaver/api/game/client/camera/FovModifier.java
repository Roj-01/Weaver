package net.weaver.api.game.client.camera;

import net.weaver.api.game.client.util.OrderedClientListener;

/**
 * Supplies a multiplicative field-of-view factor.
 *
 * <p>Modifiers are registered through
 * {@link FovModifierRegistry#register(FovModifier)} and run in ascending
 * {@link #order()} with registration order as the tie-break. Each returned
 * factor multiplies the result of the previous modifier. Return {@code 1.0} to
 * leave the field of view unchanged.
 */
public interface FovModifier extends OrderedClientListener
{
    /**
     * Returns the multiplier for this frame.
     *
     * <p>The value must be finite and greater than zero. A non-finite or
     * non-positive value is ignored and reported once per modifier id. Do not
     * retain the context.
     *
     * @param context the field-of-view snapshot, never {@code null}
     * @return the multiplicative factor, normally {@code 1.0}
     */
    float fovMultiplier(FovContext context);
}
