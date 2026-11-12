package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EntityEnteringSectionEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An entity moving into another chunk section, on server and client levels. */
@Mixin(targets = {"net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback",
        "net.minecraft.world.level.entity.TransientEntitySectionManager$Callback"})
public abstract class EntitySectionEventsMixin {
    @Shadow(remap = false)
    @Final
    private EntityAccess entity;

    @Shadow(remap = false)
    private long currentSectionKey;

    @Unique
    private long weaver$sectionBefore;

    @Inject(method = "onMove()V", at = @At("HEAD"))
    private void weaver$beforeMove(CallbackInfo callback) {
        weaver$sectionBefore = currentSectionKey;
    }

    @Inject(method = "onMove()V", at = @At("RETURN"))
    private void weaver$afterMove(CallbackInfo callback) {
        if (currentSectionKey != weaver$sectionBefore && entity instanceof Entity moved
                && GameEvents.hasListeners(EntityEnteringSectionEvent.class)) {
            GameEvents.post(new EntityEnteringSectionEvent(moved, weaver$sectionBefore, currentSectionKey), moved.level());
        }
    }
}
