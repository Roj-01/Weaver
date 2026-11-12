package com.weaver.fixture.toolchain;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/** Entrypoint for the toolchain fixture mod. */
public final class FixtureMod implements WeaverMod {

    private static final String EXPECTED_LIB = "fixture-lib-1.0.0";
    private static final String EXPECTED_TRANSITIVE = "fixture-transitive-1.0.0";
    private static final String EXPECTED_NESTED = "nested-1.0.0";

    private boolean allOk;

    @Override
    public void onInitialize(ModContext context) {
        String lib = invoke(context, "com.weaver.fixture.lib.Library");
        String transitive = invoke(context, "com.weaver.fixture.transitive.Transitive");
        String nested = invoke(context, "com.weaver.fixture.nested.NestedPayload");
        allOk = EXPECTED_LIB.equals(lib)
                && EXPECTED_TRANSITIVE.equals(transitive)
                && EXPECTED_NESTED.equals(nested);
        context.log("TOOLCHAINFIXTURE main ok=" + allOk
                + " lib=" + lib
                + " transitive=" + transitive
                + " nested=" + nested);
    }

    @Override
    public void onServerStarted(ModContext context) {
        context.log("TOOLCHAINFIXTURE RESULT ok=" + allOk + " side=" + context.side());
    }

    private static String invoke(ModContext context, String className) {
        try {
            Class<?> type = Class.forName(className, true, FixtureMod.class.getClassLoader());
            Object value = type.getMethod("value").invoke(null);
            return value == null ? "null" : value.toString();
        } catch (ReflectiveOperationException | LinkageError failure) {
            context.log("TOOLCHAINFIXTURE main ok=false reflect=" + className
                    + " exception=" + failure.getClass().getName());
            return "error:" + failure.getClass().getName();
        }
    }
}
