package net.weaver.api.game.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.weaver.api.game.client.camera.CameraRenderContext;
import net.weaver.api.game.client.camera.CameraRenderRegistry;
import net.weaver.api.game.client.camera.FovContext;
import net.weaver.api.game.client.camera.FovModifierRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dispatches the Weaver camera state and field-of-view hooks.
 *
 * <p>Targets verified against Minecraft 26.3:
 * <ul>
 *     <li>{@code Camera#extractRenderState(CameraRenderState, DeltaTracker)V}
 *     at {@code RETURN};</li>
 *     <li>the private {@code Camera#calculateFov(float)F} at {@code RETURN},
 *     cancellable so the composed value can replace the vanilla result.</li>
 * </ul>
 *
 * <p>{@code calculateFov} was chosen over {@code Projection#setupPerspective}:
 * the verified 26.3 {@code Projection#setupPerspective} descriptor is
 * {@code (FFFFF)V} (five floats, not six), and it is a lower-level path used
 * for both world and culling projection. {@code calculateFov} is the single
 * vanilla composition point for the world field of view and keeps the
 * multiplicative modifier semantics clear. The separate HUD field of view
 * computed by {@code calculateHudFov} is not modified.
 */
@Mixin(Camera.class)
public abstract class CameraRenderMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 1)
    private void weaverExtractCamera(CameraRenderState state, DeltaTracker deltaTracker,
                                     CallbackInfo callback)
    {
        if (state == null)
        {
            return;
        }
        try
        {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            CameraRenderRegistry.dispatch(new CameraRenderContext((Camera) (Object) this, state,
                    deltaTracker, partialTick, Minecraft.getInstance()));
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] CAMERA RENDER HOOK FAILED reason=" + failure);
            }
        }
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 1)
    private void weaverCalculateFov(float partialTick,
                                    CallbackInfoReturnable<Float> callback)
    {
        Float base = callback.getReturnValue();
        if (base == null)
        {
            return;
        }
        try
        {
            FovContext context = new FovContext((Camera) (Object) this, Minecraft.getInstance(),
                    partialTick, base);
            float result = FovModifierRegistry.apply(context, base);
            if (Float.compare(result, base) != 0)
            {
                callback.setReturnValue(result);
            }
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] FOV HOOK FAILED reason=" + failure);
            }
        }
    }
}
