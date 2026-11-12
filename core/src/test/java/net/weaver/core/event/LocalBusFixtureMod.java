package net.weaver.core.event;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/**
 * Minimal mod fixture with no subscribers, used to create one local bus per
 * mod without any entrypoint registration side effects.
 */
public final class LocalBusFixtureMod implements WeaverMod {

    /** {@inheritDoc} */
    @Override
    public void onInitialize(ModContext context) {
    }
}
