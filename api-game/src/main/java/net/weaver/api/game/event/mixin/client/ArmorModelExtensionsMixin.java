package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.client.model.ClientItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link ClientItemExtension#getHumanoidArmorModel}: worn items may bring their own model. */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorModelExtensionsMixin {
    @WrapOperation(method = "renderArmorPiece", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;getArmorModel(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;"))
    private HumanoidModel<?> weaver$armorModel(HumanoidArmorLayer<?, ?, ?> self, HumanoidRenderState state,
            EquipmentSlot slot, Operation<HumanoidModel<?>> original, @Local(argsOnly = true) ItemStack stack) {
        HumanoidModel<?> model = original.call(self, state, slot);
        if (stack.getItem() instanceof ClientItemExtension extension) {
            HumanoidModel<?> custom = extension.getHumanoidArmorModel(stack, slot, model);
            if (custom != null) {
                return custom;
            }
        }
        return model;
    }
}
