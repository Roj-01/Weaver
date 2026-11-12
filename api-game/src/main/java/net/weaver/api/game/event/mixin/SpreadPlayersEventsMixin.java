package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EntityTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Entities moved by the /spreadplayers command. */
@Mixin(SpreadPlayersCommand.class)
public abstract class SpreadPlayersEventsMixin {
    @WrapOperation(method = "setPlayerPositions(Ljava/util/Collection;Lnet/minecraft/server/level/ServerLevel;[Lnet/minecraft/server/commands/SpreadPlayersCommand$Position;IZ)D",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"))
    private static boolean weaver$spread(Entity entity, ServerLevel level, double x, double y, double z,
            Set<Relative> relatives, float yRot, float xRot, boolean resetCamera, Operation<Boolean> original) {
        if (!GameEvents.hasListeners(EntityTeleportEvent.class)) {
            return original.call(entity, level, x, y, z, relatives, yRot, xRot, resetCamera);
        }
        EntityTeleportEvent event = GameEvents.post(
                new EntityTeleportEvent(entity, EntityTeleportEvent.Cause.SPREAD_PLAYERS, new Vec3(x, y, z)), level);
        if (GameEvents.canceled(event)) {
            return false;
        }
        Vec3 target = event.target();
        return original.call(entity, level, target.x, target.y, target.z, relatives, yRot, xRot, resetCamera);
    }
}
