package net.weaver.hooksproof;

import net.weaver.api.ModContext;
import net.weaver.api.game.GameWeaverMod;

/** Entrypoint for the real Minecraft 26.3 hooks proof. */
public final class HooksProofMod implements GameWeaverMod {

    @Override
    public void onInitialize(ModContext context) {
        HooksProofChecks.install(context);
        context.log("PROOF INIT side=" + context.side());
    }

    @Override
    public void onServerTick(ModContext context, long tick) {
        HooksProofChecks.onServerTick(context, tick);
    }
}
