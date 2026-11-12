package net.weaver.api.game.event.mixin;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.weaver.api.game.loot.LootModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Global loot modifiers. Every roll ends in {@code getRandomItemsRaw}; a
 * top-level roll (nothing visited yet) is collected, modified, and then passed
 * on, while nested tables roll untouched.
 */
@Mixin(LootTable.class)
public abstract class LootTableModifiersMixin {
    @Inject(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$modify(LootContext context, Consumer<ItemStack> output, CallbackInfo callback) {
        if (LootModifiers.roll((LootTable) (Object) this, context,
                ((LootContextAccessor) context).weaver$visitedElements(), output)) {
            callback.cancel();
        }
    }
}
