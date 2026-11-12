package com.weaver.fixture.nested;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/** Entrypoint for the nested jar-in-jar fixture mod. */
public final class NestedMod implements WeaverMod {

    @Override
    public void onInitialize(ModContext context) {
        context.log("TOOLCHAINFIXTURE nested ok=true phase=initialize");
    }

    @Override
    public void onServerSetup(ModContext context) {
        context.log("TOOLCHAINFIXTURE nested ok=true phase=server");
    }
}
