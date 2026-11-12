package net.weaver.api.game.event.mixin.client;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.renderpearl.api.GpuFormat;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ConfigureMainRenderTargetEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The depth format of the main render target (stencil on request). */
@Mixin(MainTarget.class)
public abstract class MainTargetEventsMixin {
    @ModifyArg(method = "<init>(II)V", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;<init>(Ljava/lang/String;Lcom/mojang/renderpearl/api/GpuFormat;Lcom/mojang/renderpearl/api/GpuFormat;)V"),
            index = 2)
    private static GpuFormat weaver$depthFormat(GpuFormat depth) {
        if (!GameEvents.hasListeners(ConfigureMainRenderTargetEvent.class)) {
            return depth;
        }
        ConfigureMainRenderTargetEvent event = GameEvents.post(new ConfigureMainRenderTargetEvent(), LogicalSide.CLIENT);
        return event.stencilEnabled() ? GpuFormat.D32_FLOAT_S8_UINT : depth;
    }
}
