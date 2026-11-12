package net.weaver.api.game.event.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads which elements the vertex being written still needs (0 when it is complete). */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("elementsToFill")
    int weaver$elementsToFill();
}
