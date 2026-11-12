package net.weaver.api.game.event.mixin.extension;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link ItemExtension#onLeftClickEntity}: the main-hand item may cancel an attack. */
@Mixin(Player.class)
public abstract class PlayerExtensionsMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void weaver$leftClickEntity(Entity target, CallbackInfo callback) {
        Player self = (Player) (Object) this;
        ItemStack stack = self.getMainHandItem();
        if (stack.getItem() instanceof ItemExtension extension && extension.onLeftClickEntity(stack, self, target)) {
            callback.cancel();
        }
    }
}
