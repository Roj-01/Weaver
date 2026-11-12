package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Block;
import net.weaver.api.game.extension.ExtensionHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@code BlockExtension.getFriction} for experience orbs. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbExtensionsMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float weaver$friction(Block block, Operation<Float> original) {
        return ExtensionHooks.friction(block, (ExperienceOrb) (Object) this, original.call(block));
    }
}
