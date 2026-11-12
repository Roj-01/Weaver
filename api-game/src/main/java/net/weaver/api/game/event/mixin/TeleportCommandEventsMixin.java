package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EntityTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Every entity moved by {@code /tp} and {@code /teleport}. The event's target is
 * absolute even when the command used relative coordinates ({@code ~}); a changed
 * target is applied as an absolute position.
 */
@Mixin(TeleportCommand.class)
public abstract class TeleportCommandEventsMixin {
    @WrapOperation(method = "performTeleport", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z"))
    private static boolean weaver$commandTeleport(Entity entity, ServerLevel level, double x, double y, double z,
            Set<Relative> relatives, float yRot, float xRot, boolean resetCamera, Operation<Boolean> original) {
        if (!GameEvents.hasListeners(EntityTeleportEvent.class)) {
            return original.call(entity, level, x, y, z, relatives, yRot, xRot, resetCamera);
        }
        Vec3 absolute = new Vec3(relatives.contains(Relative.X) ? entity.getX() + x : x,
                relatives.contains(Relative.Y) ? entity.getY() + y : y,
                relatives.contains(Relative.Z) ? entity.getZ() + z : z);
        EntityTeleportEvent event = GameEvents.post(
                new EntityTeleportEvent(entity, EntityTeleportEvent.Cause.COMMAND, absolute), level);
        if (GameEvents.canceled(event)) {
            return false;
        }
        Vec3 target = event.target();
        if (target.equals(absolute)) {
            return original.call(entity, level, x, y, z, relatives, yRot, xRot, resetCamera);
        }
        // Vanilla checked the original position against the world bounds; check the new one too.
        if (!level.isInSpawnableBounds(net.minecraft.core.BlockPos.containing(target))) {
            return false;
        }
        Set<Relative> rotationOnly = EnumSet.noneOf(Relative.class);
        rotationOnly.addAll(relatives);
        rotationOnly.remove(Relative.X);
        rotationOnly.remove(Relative.Y);
        rotationOnly.remove(Relative.Z);
        return original.call(entity, level, target.x, target.y, target.z, rotationOnly, yRot, xRot, resetCamera);
    }
}
