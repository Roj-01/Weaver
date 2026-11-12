package net.weaver.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

/** ServiceLoader entry point: tells the Mixin engine which service class is ours. */
public class WeaverMixinServiceBootstrap implements IMixinServiceBootstrap {

    @Override
    public String getName() {
        return "weaver";
    }

    @Override
    public String getServiceClassName() {
        return WeaverMixinService.class.getName();
    }

    @Override
    public void bootstrap() {
        // nothing to bootstrap: the service is a plain in-JVM implementation
    }
}
