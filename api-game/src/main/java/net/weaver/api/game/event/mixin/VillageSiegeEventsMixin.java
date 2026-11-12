package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.VillageSiegeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Zombie sieges finding a spot near a village. */
@Mixin(VillageSiege.class)
public abstract class VillageSiegeEventsMixin {
    @WrapOperation(method = "tryToSetupSiege(Lnet/minecraft/server/level/ServerLevel;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/VillageSiege;findRandomSpawnPos(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 weaver$siege(VillageSiege siege, ServerLevel level, BlockPos center, Operation<Vec3> original) {
        Vec3 position = original.call(siege, level, center);
        if (position != null && GameEvents.canceled(GameEvents.post(new VillageSiegeEvent(siege, level, position), level))) {
            return null;
        }
        return position;
    }
}
