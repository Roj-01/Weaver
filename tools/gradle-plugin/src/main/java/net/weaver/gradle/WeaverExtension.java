package net.weaver.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code weaver { }} block.
 *
 * <p>All properties have conventions so a minimal mod only sets
 * {@link #getMainClass()}. New toolchain properties are additive: a 2.1 build
 * script that does not mention them keeps working unchanged.
 */
public abstract class WeaverExtension {

    public abstract Property<String> getApiJar();

    /**
     * Path to the optional game-typed API jar ({@code api-game}).
     *
     * <p>Defaults to {@code api-game/build/libs/api-game-<version>.jar} under
     * the root project, or the {@code WEAVER_GAME_API_JAR} environment
     * variable when set. The jar is added to {@code compileOnly} when it
     * exists. A missing jar only logs a warning because projects that use the
     * base API do not need it.
     *
     * @return the game API jar path
     */
    public abstract Property<String> getGameApiJar();

    public abstract Property<String> getGameDir();

    public abstract Property<String> getServerDir();

    public abstract Property<String> getAgentJar();

    public abstract Property<String> getLauncher();

    public abstract Property<String> getMainClass();

    public abstract Property<String> getModId();

    public abstract Property<String> getModName();

    public abstract Property<String> getModVersion();

    public abstract Property<String> getMinecraftVersion();

    public abstract Property<String> getSide();

    public abstract Property<String> getDatagenClass();

    public abstract ListProperty<String> getMixins();

    public abstract Property<String> getQuickPlayWorld();

    public abstract Property<String> getQuickPlayServer();

    public abstract Property<Boolean> getDryRun();

    /**
     * Jar files that are mod dependencies but are not resolved from a Maven
     * repository, for example a sibling project's {@code weaverBuild} output.
     *
     * <p>Every jar must contain {@code weaver.mod.json}. Local jars participate
     * in conflict diagnostics, compile classpath injection, and jar-in-jar
     * embedding exactly like {@code weaverMod} dependencies.
     *
     * @return the local mod file collection
     */
    public abstract ConfigurableFileCollection getLocalMods();

    /**
     * Runtime mod dependencies resolved from Maven or a flat/local repository.
     *
     * <p>Add them with {@code dependencies { weaverMod("group:name:version") }}
     * or a project/file dependency. The configuration is transitive by
     * default. Version ranges follow Gradle/Maven syntax, for example
     * {@code weaverMod("com.example:lib:[1.0,2.0)")}.
     */
    public abstract MapProperty<String, String> getDependencies();

    /**
     * Optional runtime mod dependencies, resolved for validation but not
     * embedded in the mod jar and not required at runtime.
     */
    public abstract MapProperty<String, String> getOptionalDependencies();

    /**
     * Embed resolved {@code weaverMod} and {@link #getLocalMods()} jars under
     * {@code META-INF/weaver/jars/} in the built mod jar.
     *
     * @return whether jar-in-jar embedding is enabled
     */
    public abstract Property<Boolean> getEmbedModDependencies();

    /**
     * Fail the build on version or mod-id conflicts instead of reporting them
     * and selecting the highest version deterministically.
     *
     * @return whether dependency conflicts are fatal
     */
    public abstract Property<Boolean> getStrictConflicts();

    /**
     * Add resolved {@code weaverMod} dependencies to the main source set's
     * {@code compileOnly} classpath.
     *
     * @return whether mod dependencies are compile-visible
     */
    public abstract Property<Boolean> getAddModDependenciesToCompileClasspath();

    /**
     * Extract embedded jar-in-jar entries into the run mods directory before a
     * run task starts.
     *
     * <p>This is the temporary toolchain bridge used until the loader-side
     * {@code core/jij} integration is wired into {@code LoaderAgent}; see
     * {@code docs/toolchain.md}. Set it to {@code false} once the loader
     * extracts nested jars itself to avoid staging nested mods twice.
     *
     * @return whether run tasks export nested jars
     */
    public abstract Property<Boolean> getExportJijForRun();

    /** Extra JVM arguments for {@code runClient}, {@code runServer}, and {@code runDatagen}. */
    public abstract ListProperty<String> getJvmArgs();

    /** Extra program arguments appended after the game/datagen arguments. */
    public abstract ListProperty<String> getProgramArgs();

    /** Extra environment variables for the run tasks. */
    public abstract MapProperty<String, String> getEnvironment();

    /** Start the JVM with a JDWP debug agent. */
    public abstract Property<Boolean> getDebug();

    /**
     * Developer hot swap for {@code runClient} and {@code runServer}: recompiled
     * classes of this project replace the running code (method bodies), without a
     * restart. Defaults to true.
     */
    public abstract Property<Boolean> getHotSwap();

    /** JDWP port used when {@link #getDebug()} is true; defaults to 5005. */
    public abstract Property<Integer> getDebugPort();

    /** Suspend the JVM until a debugger attaches; defaults to true. */
    public abstract Property<Boolean> getDebugSuspend();

    /**
     * Working directory for run tasks. An empty value uses the game directory
     * for the client and the server directory for the server.
     */
    public abstract Property<String> getWorkingDir();

    /** Dedicated-server port for {@code runServer}; defaults to 25566. */
    public abstract Property<Integer> getServerPort();

    /**
     * Game tests {@code weaverGameTest} runs, such as {@code examplemod:*};
     * empty (the default) runs every registered test. The
     * {@code weaver.gameTests} project property sets it from the command line.
     */
    public abstract Property<String> getGameTests();
}
