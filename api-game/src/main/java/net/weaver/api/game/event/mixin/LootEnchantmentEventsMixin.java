package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EnchantedBlockLootEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The tool's enchantment level a block loot bonus reads (Fortune). */
@Mixin(ApplyBonusCount.class)
public abstract class LootEnchantmentEventsMixin {
    @WrapOperation(method = "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getItemEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemInstance;)I"))
    private int weaver$bonusLevel(Holder<Enchantment> enchantment, ItemInstance tool, Operation<Integer> original,
            @Local(argsOnly = true) LootContext context) {
        int level = original.call(enchantment, tool);
        if (!GameEvents.hasListeners(EnchantedBlockLootEvent.class)) {
            return level;
        }
        return Math.max(0, GameEvents.post(new EnchantedBlockLootEvent(context, enchantment, tool, level), context.getLevel()).level());
    }
}
