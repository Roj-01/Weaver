package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link BlockExtension#getSoundType} for the break sound. */
@Mixin(LevelEventHandler.class)
public abstract class BreakSoundExtensionsMixin {
    @WrapOperation(method = "levelEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType weaver$breakSound(BlockState state, Operation<SoundType> original, @Local(argsOnly = true) BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return state.getBlock() instanceof BlockExtension extension && level != null
                ? extension.getSoundType(state, level, pos, null) : original.call(state);
    }
}
