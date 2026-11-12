package net.weaver.api.game.client.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Opens the package-private {@code RenderType#create} for {@code CustomRendering}. */
@Mixin(RenderType.class)
public interface RenderTypeInvoker
{
    @Invoker("create")
    static RenderType weaver$create(String name, RenderSetup setup)
    {
        throw new AssertionError("mixin not applied");
    }
}
