package com.weaver.fixture.lib;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/** Maven-published fixture library with a transitive mod dependency. */
public final class Library implements WeaverMod {

    public static String value() {
        return "fixture-lib-1.0.0";
    }

    @Override
    public void onInitialize(ModContext context) {
        context.log("TOOLCHAINFIXTURE lib ok=true value=" + value());
    }
}
