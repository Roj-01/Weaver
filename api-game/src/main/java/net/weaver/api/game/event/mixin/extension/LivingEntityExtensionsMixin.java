package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import net.weaver.api.game.extension.ExtensionHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@link BlockExtension#isClimbable}, {@link BlockExtension#getFriction}, and
 * {@link BlockExtension#makesOpenTrapdoorAboveClimbable} for living entities, and
 * {@code ItemExtension.onEntitySwing}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityExtensionsMixin {
    @WrapOperation(method = "onClimbable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean weaver$climbable(BlockState state, TagKey<Block> tag, Operation<Boolean> original) {
        if (tag == BlockTags.CLIMBABLE && state.getBlock() instanceof BlockExtension extension) {
            LivingEntity self = (LivingEntity) (Object) this;
            return extension.isClimbable(state, self.level(), self.blockPosition(), self);
        }
        return original.call(state, tag);
    }

    @WrapOperation(method = "travelInAir", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float weaver$friction(Block block, Operation<Float> original) {
        return ExtensionHooks.friction(block, (LivingEntity) (Object) this, original.call(block));
    }

    @Inject(method = "trapdoorUsableAsLadder", at = @At("HEAD"), cancellable = true)
    private void weaver$trapdoorLadder(net.minecraft.core.BlockPos pos, BlockState trapdoor,
            CallbackInfoReturnable<Boolean> callback) {
        if (!trapdoor.getValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN)) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        net.minecraft.core.BlockPos belowPos = pos.below();
        BlockState below = self.level().getBlockState(belowPos);
        if (below.getBlock() instanceof BlockExtension extension) {
            callback.setReturnValue(extension.makesOpenTrapdoorAboveClimbable(below, self.level(), belowPos, trapdoor));
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$swing(net.minecraft.world.InteractionHand hand,
            net.minecraft.world.item.component.SwingAnimation animation, boolean sendToSwingingEntity,
            CallbackInfoReturnable<Boolean> callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        net.minecraft.world.item.ItemStack stack = self.getItemInHand(hand);
        if (stack.getItem() instanceof net.weaver.api.game.extension.ItemExtension extension
                && extension.onEntitySwing(stack, self, hand)) {
            callback.setReturnValue(false);
        }
    }

    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "checkFallDamage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I"))
    private boolean weaver$landingEffects(net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.particles.ParticleOptions particle, double x, double y, double z, int count,
            double dx, double dy, double dz, double speed,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) BlockState state,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) net.minecraft.core.BlockPos pos) {
        return !(state.getBlock() instanceof BlockExtension extension
                && extension.addLandingEffects(state, level, pos, (LivingEntity) (Object) this, count));
    }

    @Inject(method = "stopUsingItem", at = @At("HEAD"))
    private void weaver$stopUsing(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        net.minecraft.world.item.ItemStack stack = self.getUseItem();
        if (self.isUsingItem() && stack.getItem() instanceof net.weaver.api.game.extension.ItemExtension extension) {
            extension.onStopUsing(stack, self, self.getUseItemRemainingTicks());
        }
    }

    @WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private net.minecraft.world.level.block.SoundType weaver$fallSound(BlockState state,
            Operation<net.minecraft.world.level.block.SoundType> original,
            @com.llamalad7.mixinextras.sugar.Local(ordinal = 0) int x, @com.llamalad7.mixinextras.sugar.Local(ordinal = 1) int y,
            @com.llamalad7.mixinextras.sugar.Local(ordinal = 2) int z) {
        LivingEntity self = (LivingEntity) (Object) this;
        return state.getBlock() instanceof BlockExtension extension
                ? extension.getSoundType(state, self.level(), new net.minecraft.core.BlockPos(x, y, z), self)
                : original.call(state);
    }
}
