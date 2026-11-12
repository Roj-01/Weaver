package net.weaver.api.game.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Client behavior of a mod's item. An {@code Item} subclass implements it next to
 * {@code ItemExtension}; it is only called on the client.
 */
public interface ClientItemExtension {
    /**
     * The arm pose of a player or humanoid mob holding this stack in {@code hand}:
     * a vanilla pose ({@code CROSSBOW_HOLD}, {@code SPYGLASS}, ...) or one a mod
     * added (see {@link ArmPoses}). Null keeps vanilla's choice.
     */
    default HumanoidModel.ArmPose getArmPose(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        return null;
    }

    /**
     * The model this stack is drawn with when worn in {@code slot} by a player or
     * humanoid mob, instead of vanilla's armor model. {@code original} is vanilla's
     * model for the slot. The model is posed from the wearer's render state and
     * drawn with the textures of the stack's equipment asset, so its texture layout
     * must match them. All its visible parts are drawn: show only the parts for
     * {@code slot} (the head for a helmet). Null keeps vanilla's model.
     */
    default HumanoidModel<?> getHumanoidArmorModel(ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot,
            HumanoidModel<?> original) {
        return null;
    }
}
