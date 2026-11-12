package net.weaver.core.dev;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.HotSwapEvent;

/**
 * Developer hot reload: every change to a mod under development reaches the
 * running game, live when the JVM allows it and otherwise by restarting the game
 * straight back into the same world or server.
 *
 * <p>Enabled with {@code -Dweaver.dev.hotswap=true}. Watched folders come from
 * {@code -Dweaver.dev.hotswap.dirs} (compiled classes, for example
 * {@code build/classes/java/main}) and {@code -Dweaver.dev.hotswap.resources}
 * (resources, for example {@code build/resources/main}), each separated by the
 * path separator. The Gradle {@code runClient} and {@code runServer} tasks set
 * them.</p>
 *
 * <table>
 *   <caption>What happens to each change</caption>
 *   <tr><th>Change</th><th>Applied</th></tr>
 *   <tr><td>method bodies of a loaded class</td><td>live ({@code Instrumentation.redefineClasses})</td></tr>
 *   <tr><td>a class not loaded yet</td><td>live: it loads the new bytes when first used</td></tr>
 *   <tr><td>a new class</td><td>live: added to the class path in an overlay jar</td></tr>
 *   <tr><td>assets and data in the resources folder</td><td>live: a pack over the mod's own, reloaded on change</td></tr>
 *   <tr><td>a class's shape (fields or methods added or removed), a mixin class, a
 *       static initializer, registrations</td><td>automatic restart into the same world
 *       or server ({@code -Dweaver.dev.restart=off} turns that off). A JVM with enhanced
 *       class redefinition (JetBrains Runtime, {@code -XX:+AllowEnhancedClassRedefinition})
 *       swaps shape changes live.</td></tr>
 * </table>
 *
 * <p>A restarted game loads the newest compiled classes even when the mod jar
 * was not rebuilt: classes whose bytes differ from the jar are served from the
 * watched folders, and classes missing from the jar come from an overlay jar.
 * Each swap is logged ({@code [weaver] HOTSWAP ...}) and posted as a
 * {@link HotSwapEvent}.</p>
 */
public final class HotSwapService {
    /** Turns hot reload on. */
    public static final String PROPERTY = "weaver.dev.hotswap";
    /** Compiled class folders to watch, separated by {@link File#pathSeparator}. */
    public static final String DIRS_PROPERTY = "weaver.dev.hotswap.dirs";
    /** Resource folders to watch (assets and data), separated by {@link File#pathSeparator}. */
    public static final String RESOURCES_PROPERTY = "weaver.dev.hotswap.resources";
    /** {@code auto} (default) restarts for changes that cannot be swapped; {@code off} only reports them. */
    public static final String RESTART_PROPERTY = "weaver.dev.restart";

    static final long POLL_MILLIS = 700L;
    static final long SETTLE_MILLIS = 300L;

    /** What the service needs from the running game; a seam for tests. */
    public interface Runtime {
        /** The loaded class with this binary name, defined by the mod class space, or null. */
        Class<?> loaded(String binaryName);

        /** Replaces the classes' bytes. */
        void redefine(List<ClassDefinition> definitions) throws Exception;

        /** The bytes of this class in the mod jars, or null when no jar has it. */
        default byte[] jarBytes(String binaryName) {
            return null;
        }

        /** Adds a jar of new classes to the mod class space. */
        default void appendJar(Path jar) throws IOException {
            throw new UnsupportedOperationException("no class path to extend");
        }

        /** Reloads the game's resources: client assets and/or server data. */
        default void reloadResources(boolean assets, boolean data) {
        }

        /** Saves and restarts the game into the same world or server. */
        default void restart(String why) {
        }
    }

    private final List<Path> roots;
    private final List<Path> resourceRoots;
    private final Runtime runtime;
    private final Predicate<String> mixinClass;
    private final Consumer<String> log;
    private final Path workDir;
    private final boolean autoRestart;
    private final Map<Path, Snapshot> known = new HashMap<>();
    private final Map<Path, Snapshot> knownResources = new HashMap<>();
    /** New bytes for classes not loaded yet, served by the transformer when they load. */
    private final Map<String, byte[]> pending = new ConcurrentHashMap<>();
    /** The bytes of each watched class as the game now runs it (to see what a change touches). */
    private final Map<String, byte[]> running = new ConcurrentHashMap<>();
    /** Classes this service added through overlay jars. */
    private final java.util.Set<String> overlaid = ConcurrentHashMap.newKeySet();
    private int overlayCount;
    private volatile boolean restartRequested;
    private volatile Thread thread;

    record Snapshot(long size, long modified, String sha) {
    }

    HotSwapService(List<Path> roots, Runtime runtime, Predicate<String> mixinClass, Consumer<String> log) {
        this(roots, List.of(), runtime, mixinClass, log, null, false);
    }

    HotSwapService(List<Path> roots, List<Path> resourceRoots, Runtime runtime, Predicate<String> mixinClass,
            Consumer<String> log, Path workDir, boolean autoRestart) {
        this.roots = List.copyOf(roots);
        this.resourceRoots = List.copyOf(resourceRoots);
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.mixinClass = Objects.requireNonNull(mixinClass, "mixinClass");
        this.log = Objects.requireNonNull(log, "log");
        this.workDir = workDir;
        this.autoRestart = autoRestart;
    }

    /** Whether hot reload was asked for. */
    public static boolean isEnabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    /** The folders named by a path-separated system property that exist. */
    static List<Path> folders(String property, Consumer<String> log) {
        List<Path> out = new ArrayList<>();
        for (String entry : System.getProperty(property, "").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                Path root = Path.of(entry.trim()).toAbsolutePath().normalize();
                if (Files.isDirectory(root)) {
                    out.add(root);
                } else {
                    log.accept("[weaver] HOTSWAP DIR MISSING dir=" + root);
                }
            }
        }
        return out;
    }

    /**
     * Starts hot reload for this boot. Call before other transformers are added, so
     * classes loaded from new bytes still pass through them. Returns null (and logs
     * why) when it cannot run.
     *
     * @param appendJar adds a jar to the mod class space (system or shared loader)
     * @param game      reloads resources and restarts the game; null before the game API is available
     */
    public static HotSwapService start(Instrumentation instrumentation, ClassLoader modLoader,
            Predicate<String> mixinClass, ThrowingConsumer<Path> appendJar, Game game, Path dataDir,
            Consumer<String> log) {
        List<Path> roots = folders(DIRS_PROPERTY, log);
        List<Path> resourceRoots = folders(RESOURCES_PROPERTY, log);
        if (roots.isEmpty() && resourceRoots.isEmpty()) {
            log.accept("[weaver] HOTSWAP UNAVAILABLE reason=no-directories (set -D" + DIRS_PROPERTY + ")");
            return null;
        }
        if (!instrumentation.isRedefineClassesSupported()) {
            log.accept("[weaver] HOTSWAP UNAVAILABLE reason=redefinition-not-supported (agent manifest)");
            return null;
        }
        Runtime runtime = new Runtime() {
            @Override
            public Class<?> loaded(String binaryName) {
                for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                    if (type.getName().equals(binaryName) && type.getClassLoader() == modLoader) {
                        return type;
                    }
                }
                return null;
            }

            @Override
            public void redefine(List<ClassDefinition> definitions) throws Exception {
                instrumentation.redefineClasses(definitions.toArray(ClassDefinition[]::new));
            }

            @Override
            public byte[] jarBytes(String binaryName) {
                try (InputStream in = modLoader.getResourceAsStream(binaryName.replace('.', '/') + ".class")) {
                    return in == null ? null : in.readAllBytes();
                } catch (IOException unreadable) {
                    return null;
                }
            }

            @Override
            public void appendJar(Path jar) throws IOException {
                try {
                    appendJar.accept(jar);
                } catch (IOException | RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IOException(failure);
                }
            }

            @Override
            public void reloadResources(boolean assets, boolean data) {
                game.reloadResources(assets, data);
            }

            @Override
            public void restart(String why) {
                game.restart(why);
            }
        };
        boolean autoRestart = !"off".equalsIgnoreCase(System.getProperty(RESTART_PROPERTY, "auto"));
        HotSwapService service = new HotSwapService(roots, resourceRoots, runtime, mixinClass, log,
                dataDir.resolve("hotswap"), autoRestart);
        service.prepareBoot();
        instrumentation.addTransformer(service.transformer(modLoader), false);
        service.watch();
        log.accept("[weaver] HOTSWAP READY dirs=" + roots + " resources=" + resourceRoots + " classes="
                + service.known.size() + " restart=" + (autoRestart ? "auto" : "off"));
        return service;
    }

    /** A consumer that may throw (adding a jar to a class loader). */
    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    /** The game side of hot reload: resource reloads and restarts (implemented in api-game). */
    public interface Game {
        void reloadResources(boolean assets, boolean data);

        void restart(String why);
    }

    /**
     * Records the watched files as they are now, and makes this boot run the newest
     * compiled code: classes whose bytes differ from the mod jars are served from the
     * watched folders when they load, classes the jars lack go into an overlay jar.
     */
    void prepareBoot() {
        baseline();
        Map<String, byte[]> missing = new LinkedHashMap<>();
        int newer = 0;
        for (Path file : classFiles()) {
            String name = binaryName(file);
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException unreadable) {
                continue;
            }
            byte[] jar = runtime.jarBytes(name);
            running.put(name, bytes);
            if (jar == null) {
                missing.put(name, bytes);
            } else if (!Arrays.equals(jar, bytes)) {
                pending.put(name.replace('.', '/'), bytes);
                newer++;
            }
        }
        if (!missing.isEmpty()) {
            try {
                overlay(missing);
            } catch (IOException | RuntimeException failure) {
                log.accept("[weaver] HOTSWAP OVERLAY FAILED reason=" + failure);
            }
        }
        if (newer > 0 || !missing.isEmpty()) {
            log.accept("[weaver] HOTSWAP BOOT newer=" + newer + " added=" + missing.size()
                    + " (the newest compiled classes run, not the jar's)");
        }
    }

    /** Records the watched files as they are now. */
    void baseline() {
        known.clear();
        for (Path file : classFiles()) {
            Snapshot snapshot = snapshot(file, null);
            if (snapshot != null) {
                known.put(file, snapshot);
            }
        }
        knownResources.clear();
        for (Path file : resourceFiles()) {
            Snapshot snapshot = snapshot(file, null);
            if (snapshot != null) {
                knownResources.put(file, snapshot);
            }
        }
    }

    /** Writes the classes into a new overlay jar and adds it to the mod class space. */
    private void overlay(Map<String, byte[]> classes) throws IOException {
        Path dir = workDir != null ? workDir : Files.createTempDirectory("weaver-hotswap");
        Files.createDirectories(dir);
        Path jar = dir.resolve("overlay-" + ProcessHandle.current().pid() + "-" + (++overlayCount) + ".jar");
        try (OutputStream raw = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(raw)) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey().replace('.', '/') + ".class"));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        runtime.appendJar(jar);
        overlaid.addAll(classes.keySet());
    }

    /**
     * Applies whatever changed since the last call. Returns the event it posted, or
     * null when no class changed (a resource change alone reloads resources and
     * posts nothing).
     */
    HotSwapEvent poll() {
        pollResources();
        Map<String, byte[]> changed = new LinkedHashMap<>();
        Map<String, byte[]> added = new LinkedHashMap<>();
        List<HotSwapEvent.Skipped> skipped = new ArrayList<>();
        for (Path file : classFiles()) {
            Snapshot before = known.get(file);
            Snapshot now = snapshot(file, before);
            if (now == null || before != null && now.sha().equals(before.sha())) {
                if (now != null) {
                    known.put(file, now);
                }
                continue;
            }
            known.put(file, now);
            String name = binaryName(file);
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException failure) {
                skipped.add(new HotSwapEvent.Skipped(name, "unreadable: " + failure.getMessage()));
                continue;
            }
            if (mixinClass.test(name)) {
                skipped.add(new HotSwapEvent.Skipped(name, "mixin class (merged into game classes at boot)"));
                continue;
            }
            if (before == null && !overlaid.contains(name) && runtime.jarBytes(name) == null) {
                added.put(name, bytes);
                running.put(name, bytes);
                continue;
            }
            String onceOnly = null;
            try {
                onceOnly = MethodPrints.restartReason(running.get(name), bytes);
            } catch (RuntimeException unreadable) {
                // not a class file we can read: let the JVM judge it
            }
            if (onceOnly != null && runtime.loaded(name) != null) {
                skipped.add(new HotSwapEvent.Skipped(name, onceOnly));
                continue;
            }
            changed.put(name, bytes);
            running.put(name, bytes);
        }
        if (changed.isEmpty() && skipped.isEmpty() && added.isEmpty()) {
            return null;
        }
        List<String> swapped = new ArrayList<>();
        if (!added.isEmpty()) {
            // new classes first: the redefined code below may refer to them
            try {
                overlay(added);
                swapped.addAll(added.keySet());
            } catch (IOException | RuntimeException failure) {
                added.keySet().forEach(name -> skipped.add(new HotSwapEvent.Skipped(name, "new class: " + failure)));
            }
        }
        Map<Class<?>, byte[]> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : changed.entrySet()) {
            Class<?> type = runtime.loaded(entry.getKey());
            if (type == null) {
                // Not loaded yet: it loads from the new bytes when first used.
                pending.put(entry.getKey().replace('.', '/'), entry.getValue());
                swapped.add(entry.getKey());
            } else {
                loaded.put(type, entry.getValue());
            }
        }
        redefine(loaded, swapped, skipped);
        HotSwapEvent event = new HotSwapEvent(swapped, skipped);
        report(event);
        if (!skipped.isEmpty()) {
            requestRestart(skipped);
        }
        return event;
    }

    private void requestRestart(List<HotSwapEvent.Skipped> skipped) {
        if (!autoRestart) {
            log.accept("[weaver] HOTSWAP RESTART NEEDED (" + RESTART_PROPERTY + "=off)");
            return;
        }
        if (restartRequested) {
            return;
        }
        restartRequested = true;
        String why = skipped.size() == 1
                ? skipped.get(0).className() + ": " + skipped.get(0).reason()
                : skipped.size() + " changes need a restart (" + skipped.get(0).className() + ", ...)";
        log.accept("[weaver] HOTSWAP RESTART requested why=" + why);
        try {
            runtime.restart(why);
        } catch (Throwable failure) {
            restartRequested = false;
            log.accept("[weaver] HOTSWAP RESTART FAILED reason=" + failure);
        }
    }

    /** Reloads the game's assets and/or data when files under the resource folders changed. */
    void pollResources() {
        if (resourceRoots.isEmpty()) {
            return;
        }
        boolean assets = false;
        boolean data = false;
        Map<Path, Snapshot> now = new HashMap<>();
        for (Path file : resourceFiles()) {
            Snapshot before = knownResources.get(file);
            Snapshot current = snapshot(file, before);
            if (current == null) {
                continue;
            }
            now.put(file, current);
            if (before == null || !before.sha().equals(current.sha())) {
                String top = top(file);
                assets |= "assets".equals(top);
                data |= "data".equals(top);
            }
        }
        for (Path gone : knownResources.keySet()) {
            if (!now.containsKey(gone)) {
                String top = top(gone);
                assets |= "assets".equals(top);
                data |= "data".equals(top);
            }
        }
        knownResources.clear();
        knownResources.putAll(now);
        if (assets || data) {
            log.accept("[weaver] HOTSWAP RESOURCES assets=" + assets + " data=" + data + " (reloading)");
            try {
                runtime.reloadResources(assets, data);
            } catch (Throwable failure) {
                log.accept("[weaver] HOTSWAP RESOURCES FAILED reason=" + failure);
            }
        }
    }

    /** The first folder under its resource root: {@code assets}, {@code data}, or another. */
    private String top(Path file) {
        for (Path root : resourceRoots) {
            if (file.startsWith(root)) {
                Path relative = root.relativize(file);
                return relative.getNameCount() > 1 ? relative.getName(0).toString() : "";
            }
        }
        return "";
    }

    private void redefine(Map<Class<?>, byte[]> loaded, List<String> swapped, List<HotSwapEvent.Skipped> skipped) {
        if (loaded.isEmpty()) {
            return;
        }
        List<ClassDefinition> all = new ArrayList<>();
        loaded.forEach((type, bytes) -> all.add(new ClassDefinition(type, bytes)));
        try {
            runtime.redefine(all);
            loaded.keySet().forEach(type -> swapped.add(type.getName()));
            return;
        } catch (Throwable together) {
            // One class's change is not allowed: swap the others one by one.
        }
        for (ClassDefinition definition : all) {
            try {
                runtime.redefine(List.of(definition));
                swapped.add(definition.getDefinitionClass().getName());
            } catch (Throwable failure) {
                skipped.add(new HotSwapEvent.Skipped(definition.getDefinitionClass().getName(), reason(failure)));
            }
        }
    }

    static String reason(Throwable failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (failure instanceof UnsupportedOperationException) {
            return "shape changed (" + message + ")";
        }
        return failure.getClass().getSimpleName() + ": " + message;
    }

    private void report(HotSwapEvent event) {
        log.accept("[weaver] HOTSWAP " + (event.complete() ? "ok" : "partial") + " swapped=" + event.swapped().size()
                + " skipped=" + event.skipped().size());
        for (HotSwapEvent.Skipped each : event.skipped()) {
            log.accept("[weaver] HOTSWAP SKIPPED class=" + each.className() + " reason=" + each.reason());
        }
        try {
            event.setLogicalSide(LogicalSide.BOTH);
            EventBus.GAME.post(event);
        } catch (Throwable failure) {
            log.accept("[weaver] HOTSWAP EVENT FAILED reason=" + failure);
        }
    }

    /** Serves the new bytes of a class that changed before the game first loaded it. */
    ClassFileTransformer transformer(ClassLoader modLoader) {
        return new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (classBeingRedefined != null || className == null || loader != modLoader || pending.isEmpty()) {
                    return null;
                }
                return pending.remove(className);
            }
        };
    }

    private void watch() {
        Thread watcher = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(POLL_MILLIS);
                    if (anyChanged()) {
                        // Let the compiler finish writing before reading.
                        do {
                            Thread.sleep(SETTLE_MILLIS);
                        } while (settling());
                        poll();
                    }
                } catch (InterruptedException stop) {
                    return;
                } catch (Throwable failure) {
                    log.accept("[weaver] HOTSWAP FAILED reason=" + failure);
                }
            }
        }, "weaver-hotswap");
        watcher.setDaemon(true);
        thread = watcher;
        watcher.start();
    }

    private Map<Path, long[]> lastSeen = Map.of();

    /** Whether a watched file's size or time differs from what was recorded, or a file appeared or vanished. */
    private boolean anyChanged() {
        if (changedAgainst(classFiles(), known)) {
            return true;
        }
        return !resourceRoots.isEmpty() && changedAgainst(resourceFiles(), knownResources);
    }

    private static boolean changedAgainst(List<Path> files, Map<Path, Snapshot> recorded) {
        if (files.size() != recorded.size()) {
            return true;
        }
        for (Path file : files) {
            Snapshot before = recorded.get(file);
            long[] now = stat(file);
            if (now != null && (before == null || before.size() != now[0] || before.modified() != now[1])) {
                return true;
            }
        }
        return false;
    }

    /** Whether files are still being written (their sizes or times moved since the last look). */
    private boolean settling() {
        Map<Path, long[]> now = new HashMap<>();
        for (Path file : classFiles()) {
            long[] stat = stat(file);
            if (stat != null) {
                now.put(file, stat);
            }
        }
        for (Path file : resourceFiles()) {
            long[] stat = stat(file);
            if (stat != null) {
                now.put(file, stat);
            }
        }
        boolean moving = !sameStats(lastSeen, now);
        lastSeen = now;
        return moving;
    }

    private static boolean sameStats(Map<Path, long[]> a, Map<Path, long[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<Path, long[]> entry : a.entrySet()) {
            long[] other = b.get(entry.getKey());
            if (other == null || other[0] != entry.getValue()[0] || other[1] != entry.getValue()[1]) {
                return false;
            }
        }
        return true;
    }

    /** Stops watching. */
    public void close() {
        Thread watcher = thread;
        if (watcher != null) {
            watcher.interrupt();
        }
    }

    List<Path> classFiles() {
        return files(roots, path -> path.toString().endsWith(".class"));
    }

    List<Path> resourceFiles() {
        return files(resourceRoots, path -> true);
    }

    private static List<Path> files(List<Path> roots, Predicate<Path> filter) {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> Files.isRegularFile(path) && filter.test(path)).forEach(files::add);
            } catch (IOException | RuntimeException skipped) {
                // a directory being rewritten; the next poll sees it
            }
        }
        return files;
    }

    String binaryName(Path file) {
        for (Path root : roots) {
            if (file.startsWith(root)) {
                String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                return relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
            }
        }
        return file.getFileName().toString();
    }

    private static long[] stat(Path file) {
        try {
            return new long[] {Files.size(file), Files.getLastModifiedTime(file).toMillis()};
        } catch (IOException gone) {
            return null;
        }
    }

    /** The file's state; reuses the hash when size and time did not change. */
    private static Snapshot snapshot(Path file, Snapshot before) {
        long[] stat = stat(file);
        if (stat == null) {
            return null;
        }
        if (before != null && before.size() == stat[0] && before.modified() == stat[1]) {
            return before;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            return new Snapshot(stat[0], stat[1], sha(bytes));
        } catch (IOException gone) {
            return null;
        }
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Pending bytes for tests. */
    Map<String, byte[]> pending() {
        return pending;
    }

    /** Opens a jar file for {@link Instrumentation#appendToSystemClassLoaderSearch}. */
    public static JarFile jarFile(Path jar) throws IOException {
        return new JarFile(jar.toFile());
    }
}
