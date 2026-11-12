package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.weaver.api.game.client.model.ArmPoses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link ArmPoses}: an added arm pose moves the arm after vanilla set it up. */
@Mixin(HumanoidModel.class)
public abstract class ArmPoseTransformMixin {
    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void weaver$poseRight(HumanoidRenderState state, CallbackInfo callback) {
        ArmPoses.Transformer transformer = ArmPoses.get(state.rightArmPose);
        if (transformer != null) {
            transformer.apply((HumanoidModel<?>) (Object) this, state, HumanoidArm.RIGHT);
        }
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void weaver$poseLeft(HumanoidRenderState state, CallbackInfo callback) {
        ArmPoses.Transformer transformer = ArmPoses.get(state.leftArmPose);
        if (transformer != null) {
            transformer.apply((HumanoidModel<?>) (Object) this, state, HumanoidArm.LEFT);
        }
    }
}
