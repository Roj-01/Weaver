package net.weaver.api.game.event.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The attributes of a supplier, to copy them when a mod adds one to an existing type. */
@Mixin(AttributeSupplier.class)
public interface AttributeSupplierAccessor {
    @Accessor("instances")
    Map<Holder<Attribute>, AttributeInstance> weaver$instances();
}
