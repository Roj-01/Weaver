package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.weaver.api.game.event.world.EntityStruckByLightningEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lightning strikes. The event fires at the call site in {@code LightningBolt#tick}
 * rather than inside {@code Entity#thunderHit}, because several entities (pigs,
 * villagers, creepers, and others) override {@code thunderHit} without calling
 * {@code super}.
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltEventsMixin {
    @WrapWithCondition(method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V"))
    private boolean weaver$strike(Entity entity, ServerLevel level, LightningBolt bolt) {
        return !GameEvents.canceled(GameEvents.post(new EntityStruckByLightningEvent(entity, bolt), level));
    }
}
