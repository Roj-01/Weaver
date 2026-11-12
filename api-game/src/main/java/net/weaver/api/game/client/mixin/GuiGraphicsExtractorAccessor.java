package net.weaver.api.game.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Opens the GUI render state for {@code GuiBlockScene}. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor
{
    @Accessor("guiRenderState")
    GuiRenderState weaver$guiRenderState();
}
