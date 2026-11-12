package com.weaver.fixture.optional;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/** Optional Maven dependency that must never be embedded in the fixture jar. */
public final class OptionalAddon implements WeaverMod {

    public static String value() {
        return "fixture-optional-1.0.0";
    }

    @Override
    public void onInitialize(ModContext context) {
        context.log("TOOLCHAINFIXTURE optional ok=true value=" + value());
    }
}
