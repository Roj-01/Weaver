package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.portal.PortalShape;
import net.weaver.api.game.event.block.PortalSpawnEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** Nether portal lighting. */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockEventsMixin {
    @WrapOperation(method = "onPlace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/portal/PortalShape;findEmptyPortalShape(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction$Axis;)Ljava/util/Optional;"))
    private Optional<PortalShape> weaver$portal(LevelAccessor level, BlockPos pos, Direction.Axis axis,
                                                Operation<Optional<PortalShape>> original) {
        Optional<PortalShape> shape = original.call(level, pos, axis);
        if (shape.isEmpty()) {
            return shape;
        }
        PortalSpawnEvent event = new PortalSpawnEvent(level, pos, shape.get());
        Level gameLevel = level instanceof Level asLevel ? asLevel : null;
        return GameEvents.canceled(GameEvents.post(event, gameLevel)) ? Optional.empty() : shape;
    }
}
