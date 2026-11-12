package net.weaver.starter;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/** Minimal starter: every lifecycle phase logs one line. */
public final class StarterMod implements WeaverMod {

    @Override
    public void onInitialize(ModContext context) {
        context.log("STARTER initialize side=" + context.side());
    }

    @Override
    public void onRegister(ModContext context) {
        context.log("STARTER register");
    }

    @Override
    public void onCommonSetup(ModContext context) {
        context.log("STARTER common");
    }

    @Override
    public void onClientSetup(ModContext context) {
        context.log("STARTER client");
    }

    @Override
    public void onServerSetup(ModContext context) {
        context.log("STARTER server");
    }

    @Override
    public void onLoadComplete(ModContext context) {
        context.log("STARTER complete");
    }
}
