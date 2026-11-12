package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.client.model.ClientItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ClientItemExtension#getArmPose} for players. */
@Mixin(AvatarRenderer.class)
public abstract class ArmPoseExtensionsMixin {
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void weaver$itemArmPose(Avatar avatar, ItemStack stack, InteractionHand hand,
            CallbackInfoReturnable<HumanoidModel.ArmPose> callback) {
        if (stack.getItem() instanceof ClientItemExtension extension) {
            HumanoidModel.ArmPose pose = extension.getArmPose(stack, avatar, hand);
            if (pose != null) {
                callback.setReturnValue(pose);
            }
        }
    }
}
