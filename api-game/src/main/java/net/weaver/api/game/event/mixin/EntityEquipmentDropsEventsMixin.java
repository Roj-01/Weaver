package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.support.DropCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * What a dying player wears joins his {@code LivingDropsEvent}:
 * {@code EntityEquipment#dropAll} (called by {@code Inventory#dropAll}) adds the
 * items straight to the level, so they are offered to the running drop capture.
 */
@Mixin(EntityEquipment.class)
public abstract class EntityEquipmentDropsEventsMixin {
    @WrapOperation(method = "dropAll(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean weaver$captureDrop(Level level, Entity spawned, Operation<Boolean> original, LivingEntity owner) {
        if (spawned instanceof ItemEntity item && DropCapture.offer(owner, item)) {
            return true;
        }
        return original.call(level, spawned);
    }
}
