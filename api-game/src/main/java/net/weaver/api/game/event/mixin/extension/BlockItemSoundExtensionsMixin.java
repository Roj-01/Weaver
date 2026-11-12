package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.extension.BlockExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link BlockExtension#getSoundType} for the place sound. */
@Mixin(BlockItem.class)
public abstract class BlockItemSoundExtensionsMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
    private SoundType weaver$placeSound(BlockState state, Operation<SoundType> original,
            @Local(argsOnly = true) BlockPlaceContext context) {
        return state.getBlock() instanceof BlockExtension extension
                ? extension.getSoundType(state, context.getLevel(), context.getClickedPos(), context.getPlayer())
                : original.call(state);
    }
}
