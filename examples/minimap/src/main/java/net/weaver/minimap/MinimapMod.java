package net.weaver.minimap;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/**
 * Weaver Minimap entry point.
 *
 * <p>The mod is client-only. All game-facing setup is deferred to
 * {@link MinimapClient}, which registers the HUD layer, screen factory, tick
 * listener, and key mappings with the Weaver client API.</p>
 */
public final class MinimapMod implements WeaverMod {

    @Override
    public void onInitialize(ModContext context) {
        context.log("MINIMAP initialize side=" + context.side());
    }

    @Override
    public void onClientSetup(ModContext context) {
        MinimapClient.install(context);
    }
}
