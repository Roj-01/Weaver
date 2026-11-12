package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a container screen's position and width, used to place the creative tab page buttons. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int weaverLeftPos();

    @Accessor("topPos")
    int weaverTopPos();

    @Accessor("imageWidth")
    int weaverImageWidth();
}
