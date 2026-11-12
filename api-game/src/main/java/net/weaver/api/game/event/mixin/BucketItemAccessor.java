package net.weaver.api.game.event.mixin;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The fluid a bucket item holds, for {@code FluidContainers}. */
@Mixin(BucketItem.class)
public interface BucketItemAccessor {
    @Accessor("content")
    Fluid weaver$content();
}
