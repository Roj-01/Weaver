package net.weaver.api.game.event.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the depth of a pose stack, so listeners cannot leave it unbalanced. */
@Mixin(PoseStack.class)
public interface PoseStackAccessor {
    @Accessor("lastIndex")
    int weaver$depth();
}
