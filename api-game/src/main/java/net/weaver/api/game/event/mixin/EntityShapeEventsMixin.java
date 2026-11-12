package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EntityConstructingEvent;
import net.weaver.api.game.event.world.EntitySizeEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Entity construction and size. */
@Mixin(Entity.class)
public abstract class EntityShapeEventsMixin {
    @Shadow
    private EntityDimensions dimensions;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
    private void weaver$constructing(EntityType<?> type, Level level, CallbackInfo callback) {
        if (GameEvents.hasListeners(EntityConstructingEvent.class)) {
            GameEvents.post(new EntityConstructingEvent((Entity) (Object) this), level);
        }
    }

    @WrapOperation(method = "refreshDimensions()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;"))
    private EntityDimensions weaver$size(Entity self, Pose pose, Operation<EntityDimensions> original) {
        EntityDimensions computed = original.call(self, pose);
        if (!GameEvents.hasListeners(EntitySizeEvent.class)) {
            return computed;
        }
        EntitySizeEvent event = GameEvents.post(new EntitySizeEvent(self, pose, dimensions, computed), self.level());
        return event.newDimensions() != null ? event.newDimensions() : computed;
    }
}
