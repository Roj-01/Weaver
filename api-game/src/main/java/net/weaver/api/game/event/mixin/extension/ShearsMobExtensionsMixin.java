package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link ItemAction#SHEAR}: mobs that vanilla shears only with shears. */
@Mixin({Sheep.class, MushroomCow.class, SnowGolem.class, CopperGolem.class, SulfurCube.class, Bogged.class})
public abstract class ShearsMobExtensionsMixin {
    @WrapOperation(method = "mobInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean weaver$shears(ItemStack stack, Object item, Operation<Boolean> original) {
        return ExtensionHooks.isOrShears(stack, item, original.call(stack, item));
    }
}
