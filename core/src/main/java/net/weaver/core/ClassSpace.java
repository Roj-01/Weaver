package net.weaver.core;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The shared class space.
 *
 * <p>The normal path appends every mod jar to the system classloader, so the
 * game, the loader, and every mod see one set of classes. Shared mode is
 * opt-in: {@link SharedMain} launches the game main through a
 * {@link SharedModClassLoader} whose parent is the system loader. Loader and
 * base API classes are shared from the agent library, while game and mod
 * classes are defined by the shared loader so the loader-gated transforms
 * apply.
 *
 * <p>Shared mode requires an explicit request plus a game classpath from
 * {@code -Dweaver.shared.classpathFile} or {@code -Dweaver.shared.classpath}.
 * When shared mode is not requested and the game is hosted by a non-system
 * loader, this class logs {@code CLASS SPACE UNSUPPORTED} and stays in system
 * mode instead of creating a shared loader that cannot transform the game.
 */
public final class ClassSpace {

    /** The class-space modes. */
    public enum Mode {
        /** Game and mod classes are hosted by the system classloader. */
        SYSTEM,
        /** Game and mod classes are hosted by one shared URL classloader. */
        SHARED
    }

    private static final String API_GAME_PROBE = "net/weaver/api/game/TypedGameEventFactory.class";
    private static final String NF_PROBE = "net/weaver/nf/DeferredRegister.class";

    private final Instrumentation instrumentation;
    private final String side;
    private final ClassLoader gameLoader;
    private final ClassLoader modLoader;
    private final Mode mode;
    private final List<java.util.jar.JarFile> appended = new ArrayList<>();

    private ClassSpace(Instrumentation instrumentation, String side, ClassLoader gameLoader,
                       ClassLoader modLoader, Mode mode) {
        this.instrumentation = instrumentation;
        this.side = side;
        this.gameLoader = gameLoader;
        this.modLoader = modLoader;
        this.mode = mode;
    }

    /**
     * Creates the class space for one boot.
     *
     * @param instrumentation the agent instrumentation
     * @param side            the process side
     * @param mods            the accepted mod jars
     * @param log             the diagnostic sink
     * @return the resolved class space
     * @throws IOException           when a mod jar cannot be appended in system mode
     * @throws IllegalStateException when shared mode is requested without a game classpath
     */
    public static ClassSpace create(Instrumentation instrumentation, String side,
                                    List<ModJar> mods, Consumer<String> log) throws IOException {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        ClassLoader resolved = resolveGameLoader(side, system);
        boolean sharedRequested = Boolean.getBoolean("weaver.classspace.shared");
        boolean gameVisibleToSystem = isGameVisible(side, system);
        Mode mode = selectMode(sharedRequested, gameVisibleToSystem);
        if (mode == Mode.SHARED) {
            List<Path> gameEntries = readGameClasspath();
            List<URL> urls = new ArrayList<>();
            for (Path entry : gameEntries) {
                urls.add(entry.toUri().toURL());
            }
            for (ModJar mod : mods) {
                urls.add(mod.path().toUri().toURL());
            }
            URL apiGame = locateJar(API_GAME_PROBE);
            if (apiGame != null) {
                urls.add(apiGame);
            }
            URL nf = locateJar(NF_PROBE);
            if (nf != null) {
                urls.add(nf);
            }
            // net.weaver.api.game and net.weaver.nf classes are defined lazily and child-first by the shared
            // loader (see SharedModClassLoader); nothing is defined eagerly here, so no game
            // class loads before the transformers are installed.
            SharedModClassLoader loader = new SharedModClassLoader(urls.toArray(URL[]::new), system);
            log.accept("[weaver] CLASS SPACE mode=shared game-entries=" + gameEntries.size()
                    + " mods=" + mods.size() + " loader=" + loader.getName()
                    + " api-game=" + (apiGame == null ? "absent" : "child-first")
                    + " nf=" + (nf == null ? "absent" : "child-first"));
            return new ClassSpace(instrumentation, side, loader, loader, Mode.SHARED);
        }
        if (!sharedRequested && !gameVisibleToSystem) {
            log.accept("[weaver] CLASS SPACE UNSUPPORTED reason=game-hosted-by-non-system-loader"
                    + " action=continue-system");
        }
        ClassSpace space = new ClassSpace(instrumentation, side, resolved, system, Mode.SYSTEM);
        for (ModJar mod : mods) {
            instrumentation.appendToSystemClassLoaderSearch(mod.jarFile());
            space.appended.add(mod.jarFile());
        }
        log.accept("[weaver] CLASS SPACE mode=system game-loader=" + resolved
                + " mods=" + mods.size());
        return space;
    }

    /**
     * Selects the class-space mode for one boot.
     *
     * <p>Shared mode is never selected implicitly: only an explicit request
     * turns it on. {@code gameVisibleToSystem} records whether the game classes
     * are reachable from the system loader and is used for diagnostics; a game
     * hosted by a non-system loader stays in system mode and is reported as
     * unsupported instead of being wrapped in a shared loader that cannot
     * transform it.
     *
     * @param requested           whether shared mode was explicitly requested
     * @param gameVisibleToSystem whether the game main class is visible to the system loader
     * @return {@link Mode#SHARED} only when {@code requested} is {@code true}, otherwise
     *         {@link Mode#SYSTEM}
     */
    public static Mode selectMode(boolean requested, boolean gameVisibleToSystem) {
        if (requested) {
            return Mode.SHARED;
        }
        return Mode.SYSTEM;
    }

    /**
     * Reads the game classpath for shared mode.
     *
     * @return the non-blank game classpath entries in declaration order
     */
    private static List<Path> readGameClasspath() {
        String file = System.getProperty("weaver.shared.classpathFile", "").trim();
        if (!file.isEmpty()) {
            Path path = Path.of(file);
            String text;
            try {
                text = Files.readString(path).trim();
            } catch (IOException failure) {
                throw new IllegalStateException("shared class space cannot read game classpath file "
                        + path + " (" + failure + ")", failure);
            }
            if (text.isEmpty()) {
                throw new IllegalStateException("shared class space game classpath file is empty: "
                        + path);
            }
            return splitClasspath(text);
        }
        String inline = System.getProperty("weaver.shared.classpath", "").trim();
        if (!inline.isEmpty()) {
            return splitClasspath(inline);
        }
        throw new IllegalStateException("shared class space requires "
                + "-Dweaver.shared.classpathFile=<file> or -Dweaver.shared.classpath=<entries>");
    }

    /**
     * Splits one classpath string on semicolons, the platform separator, or line breaks.
     *
     * @param text the raw classpath string
     * @return the non-blank entries
     */
    private static List<Path> splitClasspath(String text) {
        String regex = "[;" + Pattern.quote(File.pathSeparator) + "\\r?\\n]";
        List<Path> entries = new ArrayList<>();
        for (String entry : text.split(regex)) {
            if (!entry.isBlank()) {
                entries.add(Path.of(entry));
            }
        }
        return entries;
    }

    /**
     * Locates an agent library jar ({@code api-game}, {@code weaver-nf}) through the system loader.
     *
     * @param probe a class file the jar contains
     * @return the jar URL, or {@code null} when the jar is not on the agent library
     */
    private static URL locateJar(String probe) {
        URL marker = ClassLoader.getSystemResource(probe);
        if (marker == null) {
            return null;
        }
        try {
            if (marker.openConnection() instanceof JarURLConnection connection) {
                return connection.getJarFileURL();
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    /**
     * Returns the resource probes for one side.
     *
     * @param side the process side
     * @return the probe resources in lookup order
     */
    private static String[] probes(String side) {
        return "server".equals(side)
                ? new String[]{"net/minecraft/server/Main.class",
                    "net/minecraft/server/MinecraftServer.class"}
                : new String[]{"net/minecraft/client/main/Main.class",
                    "net/minecraft/client/Minecraft.class"};
    }

    /**
     * Reports whether the game main class is visible to one loader.
     *
     * @param side   the process side
     * @param loader the loader to inspect
     * @return {@code true} when a game probe resolves from the loader
     */
    private static boolean isGameVisible(String side, ClassLoader loader) {
        for (String probe : probes(side)) {
            if (loader.getResource(probe) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the loader that hosts the game classes.
     *
     * @param side   the process side
     * @param system the system loader
     * @return the system loader or the current context loader when it hosts the game
     */
    private static ClassLoader resolveGameLoader(String side, ClassLoader system) {
        for (String probe : probes(side)) {
            if (system.getResource(probe) != null) {
                return system;
            }
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context != null && context != system && context.getResource(probe) != null) {
                return context;
            }
        }
        return system;
    }

    /**
     * Returns the loader that hosts game classes.
     *
     * @return the game loader
     */
    public ClassLoader gameLoader() {
        return gameLoader;
    }

    /**
     * Returns the loader that hosts mod classes.
     *
     * @return the mod loader
     */
    public ClassLoader modLoader() {
        return modLoader;
    }

    /**
     * Returns the process side.
     *
     * @return the side
     */
    public String side() {
        return side;
    }

    /**
     * Reports whether this class space is shared.
     *
     * @return {@code true} in shared mode
     */
    public boolean shared() {
        return mode == Mode.SHARED;
    }

    /**
     * Returns the resolved class-space mode.
     *
     * @return the mode
     */
    public Mode mode() {
        return mode;
    }
}
