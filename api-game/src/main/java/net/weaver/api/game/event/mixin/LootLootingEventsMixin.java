package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.EnchantedEntityLootEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The attacker's enchantment level an entity loot bonus reads (Looting). */
@Mixin(EnchantedCountIncreaseFunction.class)
public abstract class LootLootingEventsMixin {
    @WrapOperation(method = "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int weaver$lootingLevel(Holder<Enchantment> enchantment, LivingEntity attacker, Operation<Integer> original,
            @Local(argsOnly = true) LootContext context) {
        int level = original.call(enchantment, attacker);
        if (!GameEvents.hasListeners(EnchantedEntityLootEvent.class)) {
            return level;
        }
        return Math.max(0, GameEvents.post(new EnchantedEntityLootEvent(context, enchantment, attacker, level), context.getLevel()).level());
    }
}
