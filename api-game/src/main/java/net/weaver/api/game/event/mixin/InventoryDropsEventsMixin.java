package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.support.DropCapture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A dying player's inventory joins his {@code LivingDropsEvent}: the items
 * {@code Inventory#dropAll} adds straight to the level are offered to the
 * running drop capture (they bypass {@code Entity#spawnAtLocation}).
 */
@Mixin(Inventory.class)
public abstract class InventoryDropsEventsMixin {
    @Shadow
    @Final
    public Player player;

    @WrapOperation(method = "dropAll()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean weaver$captureDrop(Level level, Entity spawned, Operation<Boolean> original) {
        if (spawned instanceof ItemEntity item && DropCapture.offer(this.player, item)) {
            return true;
        }
        return original.call(level, spawned);
    }
}
