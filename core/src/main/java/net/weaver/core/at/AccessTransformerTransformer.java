package net.weaver.core.at;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

/** Applies access-transformer rules before Mixin sees a class. */
public final class AccessTransformerTransformer implements ClassFileTransformer {

    private static final String[] JDK_PREFIXES = {
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "org/w3c/", "org/xml/"
    };

    private final AccessTransformer transformer;

    public AccessTransformerTransformer(AccessTransformer transformer) {
        this.transformer = transformer;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null || !transformer.hasRules()) {
            return null;
        }
        if (isJdk(className)) {
            return null;
        }
        String dotted = className.replace('/', '.');
        if (!transformer.hasRulesFor(dotted)) {
            return null;
        }
        try {
            return transformer.apply(classfileBuffer);
        } catch (Throwable failure) {
            System.out.println("[weaver] AT FAILED class=" + dotted + " reason=" + failure);
            return null;
        }
    }

    private static boolean isJdk(String internalName) {
        for (String prefix : JDK_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
