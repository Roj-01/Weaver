package net.weaver.core.enums;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/** Runs {@link EnumExtender} on the enums mods extend, before Mixin sees them. */
public final class EnumExtensionTransformer implements ClassFileTransformer {
    private final EnumExtender extender;

    public EnumExtensionTransformer(EnumExtender extender) {
        this.extender = extender;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null || classBeingRedefined != null
                || !extender.targets(className)) {
            return null;
        }
        try {
            return extender.apply(classfileBuffer);
        } catch (Throwable failure) {
            System.out.println("[weaver] ENUM EXTENSION FAILED class=" + className.replace('/', '.') + " reason=" + failure);
            return null;
        }
    }
}
