package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.ItemExpireEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dropped item expiry. The second {@code discard} in {@code ItemEntity#tick}
 * is the one guarded by {@code age >= 6000}.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityEventsMixin {
    // -32768 marks an item that never despawns; extra life must not reach it.
    @org.spongepowered.asm.mixin.Unique
    private static final int WEAVER_MIN_AGE = -32767;

    @Shadow
    private int age;

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
    private void weaver$expire(ItemEntity self, Operation<Void> original) {
        if (!GameEvents.hasListeners(ItemExpireEvent.class)) {
            original.call(self);
            return;
        }
        ItemExpireEvent event = GameEvents.post(new ItemExpireEvent(self), self.level());
        if (event.extraLife() > 0) {
            age = Math.max(WEAVER_MIN_AGE, age - event.extraLife());
            return;
        }
        original.call(self);
    }
}
