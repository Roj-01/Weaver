package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.RenderItemInFrameEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The map or item drawn inside an item frame. The frame state is kept from
 * the start of {@code submit} for the two content calls (map or item).
 */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererEventsMixin {
    @Unique
    private ItemFrameRenderState weaver$state;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void weaver$frameStart(ItemFrameRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                   CameraRenderState camera, CallbackInfo callback) {
        weaver$state = state;
    }

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MapRenderer;render(Lnet/minecraft/client/renderer/state/MapRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ZI)V"))
    private void weaver$map(MapRenderer maps, MapRenderState map, PoseStack poseStack, SubmitNodeCollector collector,
                            boolean framed, int light, Operation<Void> original) {
        if (!weaver$canceled(poseStack, collector)) {
            original.call(maps, map, poseStack, collector, framed, light);
        }
    }

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void weaver$item(ItemStackRenderState item, PoseStack poseStack, SubmitNodeCollector collector, int light,
                             int overlay, int outline, Operation<Void> original) {
        if (!weaver$canceled(poseStack, collector)) {
            original.call(item, poseStack, collector, light, overlay, outline);
        }
    }

    @Unique
    private boolean weaver$canceled(PoseStack poseStack, SubmitNodeCollector collector) {
        ItemFrameRenderState state = weaver$state;
        if (state == null || !GameEvents.hasListeners(RenderItemInFrameEvent.class)) {
            return false;
        }
        int depth = net.weaver.api.game.event.support.PoseStacks.depth(poseStack);
        boolean canceled = GameEvents.canceled(GameEvents.post(new RenderItemInFrameEvent(state,
                (ItemFrameRenderer<?>) (Object) this, poseStack, collector), LogicalSide.CLIENT));
        net.weaver.api.game.event.support.PoseStacks.restore(poseStack, depth, "RenderItemInFrameEvent");
        return canceled;
    }
}
