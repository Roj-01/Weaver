package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.client.model.ClientItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ClientItemExtension#getArmPose} for humanoid mobs. */
@Mixin(HumanoidMobRenderer.class)
public abstract class MobArmPoseExtensionsMixin {
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private void weaver$itemArmPose(Mob mob, HumanoidArm arm, CallbackInfoReturnable<HumanoidModel.ArmPose> callback) {
        ItemStack stack = mob.getItemHeldByArm(arm);
        if (stack.getItem() instanceof ClientItemExtension extension) {
            InteractionHand hand = arm == mob.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            HumanoidModel.ArmPose pose = extension.getArmPose(stack, mob, hand);
            if (pose != null) {
                callback.setReturnValue(pose);
            }
        }
    }
}
