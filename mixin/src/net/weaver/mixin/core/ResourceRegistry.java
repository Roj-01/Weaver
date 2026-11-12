package net.weaver.mixin.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Per-process resource and class routing for the Mixin engine.
 *
 * <p>The upstream engine asks a single global {@code IMixinService} for configs, refmaps and
 * class bytes. Weaver keeps those lookups mod-scoped here: the config/refmap buffers are
 * registered under synthetic names, and every mixin class is bound to the loader of the mod that
 * declared it. Nothing is resolved through the system loader before the owning mod loader.
 */
public final class ResourceRegistry {

    private final Map<String, byte[]> resources = new ConcurrentHashMap<>();
    private final Map<String, ClassLoader> modClasses = new ConcurrentHashMap<>();
    /**
     * Reference counts for resources shared by more than one registration (a parent config that
     * several children inherit from, and its refmap). Without the count, removing the first child
     * would unbind the parent while the second child still expects it to load.
     */
    private final Map<String, Integer> sharedRefs = new ConcurrentHashMap<>();
    /**
     * Bounded class-byte cache. The engine reads the same game class several times per config
     * (target discovery, optional-target probe, prepare, transform), and every read is a fresh
     * {@code getResourceAsStream}. Entries are immutable byte arrays; callers must not mutate the
     * returned buffer, and Mixin always copies before rewriting.
     */
    private static final int CACHE_LIMIT = 1024;
    private final Map<CacheKey, byte[]> bytesCache = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<CacheKey> cacheOrder =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    // Insertion order (arm order), not ConcurrentHashMap's hash order: two mod loaders that both
    // expose the same class name must resolve to one deterministic owner, otherwise Mixin class
    // identity depends on JVM iteration order between runs.
    private final Set<ClassLoader> modLoaders =
            Collections.synchronizedSet(new LinkedHashSet<ClassLoader>());
    private volatile ClassLoader gameLoader;
    private final ClassLoader engineLoader = ResourceRegistry.class.getClassLoader();

    void gameLoader(ClassLoader loader) {
        this.gameLoader = loader;
    }

    /** The loader that owns the game classes (used to reach provider classes by name). */
    public ClassLoader gameClassLoader() {
        return this.gameLoader;
    }

    ClassLoader gameLoader() {
        return this.gameLoader;
    }

    /** Registers bytes under an exact resource name (configs, resolved refmaps). */
    public void bind(String name, byte[] bytes) {
        this.resources.put(name, bytes.clone());
    }

    public void unbind(String name) {
        this.resources.remove(name);
        this.sharedRefs.remove(name);
    }

    /** Binds a shared resource and counts one more owner. */
    public void retain(String name, byte[] bytes) {
        this.resources.put(name, bytes.clone());
        this.sharedRefs.merge(name, 1, Integer::sum);
    }

    /** Drops one owner of a shared resource; the last owner unbinds it. Returns true if unbound. */
    public boolean release(String name) {
        Integer remaining = this.sharedRefs.computeIfPresent(name, (key, count) -> count <= 1 ? null : count - 1);
        if (remaining != null) {
            return false;
        }
        this.sharedRefs.remove(name);
        this.resources.remove(name);
        return true;
    }

    public int owners(String name) {
        return this.sharedRefs.getOrDefault(name, 0);
    }

    public boolean isBound(String name) {
        return this.resources.containsKey(name);
    }

    /** Binds a binary class name to the loader allowed to read it. */
    public void bindClass(String binaryName, ClassLoader loader) {
        this.modClasses.put(binaryName, loader);
    }

    public void unbindClass(String binaryName) {
        this.modClasses.remove(binaryName);
    }

    public ClassLoader loaderOf(String binaryName) {
        return this.modClasses.get(binaryName);
    }

    /**
     * Registers a mod loader as a fallback for classes that are neither bound by name nor present
     * in the game loader. Mixins may target classes shipped by the declaring mod (not only game
     * classes), so preparation must be able to read them without loading the class.
     */
    public void addModLoader(ClassLoader loader) {
        if (loader != null) {
            this.modLoaders.add(loader);
        }
    }

    public void removeModLoader(ClassLoader loader) {
        if (loader != null) {
            this.modLoaders.remove(loader);
            invalidate(loader);
        }
    }

    /** Name of the owning loader, for markers. */
    public String loaderName(String binaryName) {
        ClassLoader loader = this.modClasses.get(binaryName);
        if (loader == null) {
            return "game";
        }
        String name = loader.getName();
        return name == null || name.isBlank() ? loader.getClass().getSimpleName() : name;
    }

    /** Config/refmap lookup used by {@code IMixinService.getResourceAsStream}. */
    public InputStream open(String name) {
        byte[] bytes = this.resources.get(name);
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        ClassLoader game = this.gameLoader;
        if (game != null) {
            InputStream in = game.getResourceAsStream(name);
            if (in != null) {
                return in;
            }
        }
        return this.engineLoader == null ? null : this.engineLoader.getResourceAsStream(name);
    }

    /** Class lookup used by {@code IClassProvider.findClass}. */
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        ClassLoader owner = this.modClasses.get(name);
        if (owner != null) {
            return Class.forName(name, initialize, owner);
        }
        for (ClassLoader loader : loaderSnapshot()) {
            try {
                return Class.forName(name, initialize, loader);
            } catch (ClassNotFoundException ignored) {
                // keep looking: another mod loader may own it
            }
        }
        ClassLoader game = this.gameLoader;
        if (game != null) {
            try {
                return Class.forName(name, initialize, game);
            } catch (ClassNotFoundException ignored) {
                // not a game class either; fall back to the engine loader
            }
        }
        return Class.forName(name, initialize, this.engineLoader);
    }

    /** ASM supply used by {@code IClassBytecodeProvider.getClassNode}. */
    public ClassNode classNode(String name) throws ClassNotFoundException, IOException {
        byte[] bytes = classBytes(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        reader.accept(node, ClassReader.SKIP_FRAMES);
        return node;
    }

    /** Raw bytes of a class, mod loader first, then game loader, then the engine loader. */
    public byte[] classBytes(String name) {
        String path = name.replace('.', '/') + ".class";
        ClassLoader owner = this.modClasses.get(name);
        byte[] bytes = owner == null ? null : readCached(owner, path);
        if (bytes != null) {
            return bytes;
        }
        for (ClassLoader loader : loaderSnapshot()) {
            bytes = readCached(loader, path);
            if (bytes != null) {
                return bytes;
            }
        }
        ClassLoader game = this.gameLoader;
        bytes = game == null ? null : readCached(game, path);
        if (bytes != null) {
            return bytes;
        }
        return this.engineLoader == null ? null : readCached(this.engineLoader, path);
    }

    /** Cache key: class bytes are per (loader, path); two mods may ship the same path. */
    private record CacheKey(ClassLoader loader, String path) {
    }

    private byte[] readCached(ClassLoader loader, String path) {
        CacheKey key = new CacheKey(loader, path);
        byte[] cached = this.bytesCache.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] bytes = read(loader, path);
        if (bytes == null) {
            return null;
        }
        if (this.bytesCache.size() >= CACHE_LIMIT) {
            CacheKey evicted = this.cacheOrder.poll();
            if (evicted != null) {
                this.bytesCache.remove(evicted);
            }
        }
        this.bytesCache.put(key, bytes);
        this.cacheOrder.add(key);
        return bytes;
    }

    /** Number of cached class buffers (diagnostics). */
    public int cacheSize() {
        return this.bytesCache.size();
    }

    /** Drops every cached buffer of one loader (removal / reload). */
    public void invalidate(ClassLoader loader) {
        if (loader == null) {
            return;
        }
        this.bytesCache.keySet().removeIf(key -> key.loader() == loader);
        this.cacheOrder.removeIf(key -> key.loader() == loader);
    }

    /** Cached read for one explicit loader (config parsing reads each mixin class several times). */
    public byte[] cachedResource(ClassLoader loader, String path) {
        return loader == null ? null : readCached(loader, path);
    }

    /** Stable snapshot so concurrent arm/remove cannot change iteration order mid-lookup. */
    public List<ClassLoader> loaderSnapshot() {
        synchronized (this.modLoaders) {
            return List.copyOf(this.modLoaders);
        }
    }

    /** Reads a resource from one specific loader (used to pull a mod's refmap). */
    public static byte[] resource(ClassLoader loader, String name) {
        return loader == null ? null : read(loader, name);
    }

    private static byte[] read(ClassLoader loader, String path) {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
