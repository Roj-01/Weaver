package net.weaver.mixin.core;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/**
 * Load-time weaving of registered targets.
 *
 * <p>Only classes an active config declares ever reach the upstream engine. Everything else is a
 * fast skip. Any fault returns {@code null}, which makes the JVM define the original bytes it
 * passed in.
 */
public final class WeaverMixinTransformer implements ClassFileTransformer {

    private static final String[] SKIP_PREFIXES = {
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "org/w3c/", "org/xml/",
            "org/spongepowered/", "org/objectweb/asm/", "net/weaver/mixin/"
    };

    private final MixinEngine engine;

    public WeaverMixinTransformer(MixinEngine engine) {
        this.engine = engine;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        String dotted = className.replace('/', '.');
        try {
            boolean interesting = this.engine.hasTargets(dotted) || this.engine.isMixinClass(dotted);
            if (this.engine.traces(dotted)) {
                this.engine.traceSeen(dotted, loader, interesting);
            }
            if (!interesting) {
                return null;
            }
            this.engine.noteLoaded(className);
            if (classBeingRedefined != null) {
                Marker.emit("MIXIN LIVE UNSUPPORTED target=" + dotted
                        + " reason=class-redefinition-not-supported"
                        + " action=loaded-bytes-unchanged");
                return null;
            }
            if (skip(className)) {
                return null;
            }
            byte[] woven = this.engine.transform(className, classfileBuffer, loader);
            if (woven == classfileBuffer) {
                return null;
            }
            return woven;
        } catch (Throwable t) {
            Marker.emit("MIXIN FAILSAFE target=" + dotted + " reason=" + Marker.oneLine(t)
                    + " action=original-bytes");
            return null;
        }
    }

    private static boolean skip(String internalName) {
        for (String prefix : SKIP_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
