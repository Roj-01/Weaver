package net.weaver.api.game.event.mixin;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.weaver.api.game.entity.MultipartEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Entity queries by box find the parts of multipart entities, as they find ender dragon parts. */
@Mixin(Level.class)
public abstract class MultipartLevelMixin {
    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"))
    private void weaver$partsUntyped(Entity except, AABB box, Predicate<? super Entity> filter,
            CallbackInfoReturnable<List<Entity>> callback) {
        List<Entity> found = callback.getReturnValue();
        if (found != null) {
            MultipartEntities.addParts((Level) (Object) this, except, box, filter, found);
        }
    }

    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;Ljava/util/List;I)V",
            at = @At("RETURN"))
    private <T extends Entity> void weaver$partsTyped(EntityTypeTest<Entity, T> test, AABB box, Predicate<? super T> filter,
            List<? super T> out, int limit, CallbackInfo callback) {
        MultipartEntities.addParts((Level) (Object) this, test, box, filter, out, limit);
    }
}
