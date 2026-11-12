package com.weaver.fixture.transitive;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/** Transitive Maven dependency of the fixture library. */
public final class Transitive implements WeaverMod {

    public static String value() {
        return "fixture-transitive-1.0.0";
    }

    @Override
    public void onInitialize(ModContext context) {
        context.log("TOOLCHAINFIXTURE transitive ok=true value=" + value());
    }
}
