package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.living.LivingGetProjectileEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The ammunition a shooter picks; each class overrides getProjectile without calling super. */
@Mixin({LivingEntity.class, Player.class, Monster.class})
public abstract class ProjectileChoiceEventsMixin {
    @Inject(method = "getProjectile(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private void weaver$projectile(ItemStack weapon, CallbackInfoReturnable<ItemStack> callback) {
        if (GameEvents.hasListeners(LivingGetProjectileEvent.class)) {
            LivingEntity self = (LivingEntity) (Object) this;
            LivingGetProjectileEvent event = GameEvents.post(
                    new LivingGetProjectileEvent(self, weapon, callback.getReturnValue()), self.level());
            callback.setReturnValue(event.projectile() == null ? ItemStack.EMPTY : event.projectile());
        }
    }
}
