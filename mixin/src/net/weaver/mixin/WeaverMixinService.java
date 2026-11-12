package net.weaver.mixin;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment.CompatibilityLevel;
import org.spongepowered.asm.service.IAdviceProvider;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IFeatureValidator;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;

/**
 * Minimal IMixinService binding the real SpongePowered Mixin engine to the
 * Weaver class-loading pipeline.
 *
 * Contract implemented (the minimum the engine needs):
 *   findClass  -> IClassProvider.findClass / findAgentClass
 *   transform  -> IClassBytecodeProvider.getClassNode (ASM ClassNode from our bytes)
 *   reload     -> WeaverMixinService#reload is NOT part of IMixinService in 0.8.x;
 *                 the engine's reload hook lives in IMixinTransformer#reload and is
 *                 exposed through MixinBridge#reload.
 */
public class WeaverMixinService extends MixinServiceAbstract {

    /** Names already defined by our loader; used by isClassLoaded (no premature loading). */
    private static final Set<String> DEFINED = Collections.synchronizedSet(new LinkedHashSet<String>());

    private final IClassProvider classProvider = new WeaverClassProvider();
    private final IClassBytecodeProvider bytecodeProvider = new WeaverBytecodeProvider();
    private final IClassTracker classTracker = new WeaverClassTracker();
    private final ITransformerProvider transformerProvider = new WeaverTransformerProvider();
    private final IMixinAuditTrail auditTrail = new WeaverAuditTrail();

    public static void markDefined(String internalName) {
        DEFINED.add(internalName);
    }

    static boolean isDefined(String internalName) {
        return DEFINED.contains(internalName);
    }

    /** Public view for the production engine ({@code MixinEngine#isLoaded}). */
    public static boolean defined(String internalName) {
        return DEFINED.contains(internalName);
    }

    @Override
    public String getName() {
        return "weaver";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this.classProvider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this.bytecodeProvider;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return this.transformerProvider;
    }

    @Override
    public IClassTracker getClassTracker() {
        return this.classTracker;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return this.auditTrail;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.emptyList();
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return IAdviceProvider.GENERIC;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    private final IContainerHandle primaryContainer = new WeaverContainerHandle();

    @Override
    public IContainerHandle getPrimaryContainer() {
        return this.primaryContainer;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream registered = net.weaver.mixin.core.MixinEngine.get().resources().open(name);
        return registered != null ? registered
                : WeaverMixinService.class.getClassLoader().getResourceAsStream(name);
    }

    @Override
    public CompatibilityLevel getMinCompatibilityLevel() {
        return CompatibilityLevel.JAVA_8;
    }

    @Override
    public CompatibilityLevel getMaxCompatibilityLevel() {
        // Minecraft 26.3 ships class files at Java 25 and AppleSkin declares
        // "compatibilityLevel": "JAVA_25"; the Fabric fork of Mixin 0.8.7 has the level.
        return CompatibilityLevel.JAVA_25;
    }

    @Override
    protected ILogger createLogger(String name) {
        return new net.weaver.mixin.WeaverMarkerLogger(name);
    }

    // ---- inner implementations -------------------------------------------

    static final class WeaverClassProvider implements IClassProvider {
        private static final ClassLoader CL = WeaverMixinService.class.getClassLoader();

        @Override
        public URL[] getClassPath() {
            String cp = System.getProperty("java.class.path", "");
            String[] parts = cp.split(java.io.File.pathSeparator);
            URL[] urls = new URL[parts.length];
            int n = 0;
            for (String p : parts) {
                if (p.isEmpty()) continue;
                try {
                    urls[n++] = new java.io.File(p).toURI().toURL();
                } catch (java.net.MalformedURLException ignored) {
                }
            }
            URL[] trimmed = new URL[n];
            System.arraycopy(urls, 0, trimmed, 0, n);
            return trimmed;
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            return findClass(name, true);
        }

        @Override
        public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
            try {
                return net.weaver.mixin.core.MixinEngine.get().resources().findClass(name, initialize);
            } catch (ClassNotFoundException notFound) {
                // Only Mixin's own generated helpers may be defined from here. A synthetic of a
                // mixin target must be defined by the host in the target's loader and nest; creating
                // it through this loader caches a class the woven target cannot link against.
                if (name.startsWith("org.spongepowered.asm.synthetic.")) {
                    Class<?> generated = net.weaver.mixin.MixinBridge.defineGenerated(name);
                    if (generated != null) {
                        return generated;
                    }
                }
                throw notFound;
            }
        }

        @Override
        public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
            return findClass(name, initialize);
        }
    }

    static final class WeaverClassTracker implements IClassTracker {
        @Override
        public void registerInvalidClass(String className) {
            // no-op: we do not track per-class validity yet
        }

        @Override
        public boolean isClassLoaded(String className) {
            return isDefined(className)
                    || net.weaver.mixin.core.MixinEngine.get().isLoaded(className);
        }

        @Override
        public String getClassRestrictions(String className) {
            return "";
        }
    }

    static final class WeaverTransformerProvider implements ITransformerProvider {
        @Override
        public Collection<ITransformer> getTransformers() {
            return Collections.emptyList();
        }

        @Override
        public Collection<ITransformer> getDelegatedTransformers() {
            return Collections.emptyList();
        }

        @Override
        public void addTransformerExclusion(String name) {
            // no-op
        }
    }

    static final class WeaverAuditTrail implements IMixinAuditTrail {
        private static final java.util.List<String> EVENTS =
                Collections.synchronizedList(new java.util.ArrayList<String>());

        @Override
        public void onApply(String configName, String mixinName) {
            EVENTS.add("apply " + configName + " " + mixinName);
            net.weaver.mixin.core.Audit.onApply(configName, mixinName);
        }

        @Override
        public void onPostProcess(String className) {
            EVENTS.add("post " + className);
            net.weaver.mixin.core.Audit.onPostProcess(className);
        }

        @Override
        public void onGenerate(String configName, String className) {
            EVENTS.add("generate " + configName + " " + className);
            net.weaver.mixin.core.Audit.onGenerate(configName, className);
        }

        public static java.util.List<String> events() {
            return EVENTS;
        }
    }
}
