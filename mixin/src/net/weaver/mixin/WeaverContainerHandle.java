package net.weaver.mixin;

import java.util.Collection;
import java.util.Collections;

import org.spongepowered.asm.launch.platform.container.IContainerHandle;

/** Single synthetic container representing the Weaver runtime itself. */
public class WeaverContainerHandle implements IContainerHandle {

    public static final String ID = "weaver-runtime";

    @Override
    public String getAttribute(String name) {
        if ("classLoader".equals(name)) {
            return String.valueOf(WeaverContainerHandle.class.getClassLoader());
        }
        if ("name".equals(name)) {
            return ID;
        }
        return null;
    }

    @Override
    public Collection<IContainerHandle> getNestedContainers() {
        return Collections.emptyList();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Weaver runtime (in-JVM)";
    }

    @Override
    public String toString() {
        return ID;
    }
}
